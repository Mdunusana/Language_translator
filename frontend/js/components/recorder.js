// Microphone recording with MediaRecorder, converted to 16 kHz mono WAV with the Web Audio API.
//
// Why convert? Browsers record in different containers (Chrome/Edge/Firefox: WebM or Ogg Opus,
// Safari: MP4/AAC) and speech providers accept different subsets. Decoding whatever the browser
// produced and re-encoding it as plain PCM WAV gives the backend a single, verifiable format.

import { encodeWav } from './wav.js';

export const TARGET_SAMPLE_RATE = 16000;

/** Error with a stable `code` the UI can react to. */
export class RecorderError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'RecorderError';
    this.code = code;
  }
}

const AudioContextClass = window.AudioContext || window.webkitAudioContext;

export function isRecordingSupported() {
  return Boolean(window.isSecureContext && navigator.mediaDevices?.getUserMedia && window.MediaRecorder && AudioContextClass);
}

function toRecorderError(error) {
  switch (error?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return new RecorderError('permission-denied',
        'Microphone access is blocked. Allow the microphone for this site (use the icon in the address bar), then try again.');
    case 'NotFoundError':
    case 'OverconstrainedError':
      return new RecorderError('no-microphone', 'No microphone was found. Connect one and try again.');
    case 'NotReadableError':
    case 'AbortError':
      return new RecorderError('microphone-busy', 'Your microphone is being used by another application. Close it and try again.');
    default:
      return new RecorderError('unknown', 'The microphone could not be started. Please try again.');
  }
}

/** Decodes any recording the browser made and resamples it to 16 kHz mono. */
export async function toWav16kMono(recording) {
  const context = new AudioContextClass();
  let decoded;
  try {
    decoded = await context.decodeAudioData(await recording.arrayBuffer());
  } catch {
    throw new RecorderError('decode-failed', 'The recording could not be processed. Please try again.');
  } finally {
    context.close();
  }
  const frames = Math.max(1, Math.ceil(decoded.duration * TARGET_SAMPLE_RATE));
  const offline = new OfflineAudioContext(1, frames, TARGET_SAMPLE_RATE); // 1 channel: the browser down-mixes
  const source = offline.createBufferSource();
  source.buffer = decoded;
  source.connect(offline.destination);
  source.start();
  const rendered = await offline.startRendering();
  return {
    blob: new Blob([encodeWav(rendered.getChannelData(0), TARGET_SAMPLE_RATE)], { type: 'audio/wav' }),
    durationSeconds: decoded.duration,
  };
}

export class VoiceRecorder {
  /**
   * @param {object} [options]
   * @param {number} [options.maxSeconds=60]
   * @param {(seconds: number) => void} [options.onTick]   elapsed time, ~5 times per second
   * @param {(level: number) => void} [options.onLevel]    input level 0..1 for a level meter
   * @param {() => void} [options.onLimit]                 called once when maxSeconds is reached
   */
  constructor({ maxSeconds = 60, onTick, onLevel, onLimit } = {}) {
    Object.assign(this, { maxSeconds, onTick, onLevel, onLimit });
    this.chunks = [];
  }

  /**
   * Asks for microphone permission (first time only) and starts recording.
   * @param {MediaStream} [stream] use an existing stream instead of the microphone (used by automated checks)
   * @throws {RecorderError}
   */
  async start(stream) {
    if (!isRecordingSupported()) {
      throw new RecorderError('unsupported', 'Voice recording is not supported in this browser.');
    }
    // Create the level-meter AudioContext now, while we are still inside the user's click. Browsers' autoplay
    // rules start contexts created later (e.g. after the permission prompt) in a suspended, silent state.
    if (this.onLevel) this.meterContext = new AudioContextClass();
    try {
      this.stream = stream ?? await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
    } catch (error) {
      this.release();
      throw toRecorderError(error);
    }

    this.mediaRecorder = new MediaRecorder(this.stream);
    this.mediaRecorder.addEventListener('dataavailable', (event) => {
      if (event.data.size > 0) this.chunks.push(event.data);
    });
    this.mediaRecorder.start(250);
    this.startedAt = performance.now();

    this.timer = setInterval(() => {
      const seconds = this.elapsedSeconds;
      this.onTick?.(seconds);
      if (seconds >= this.maxSeconds && !this.limitReached) {
        this.limitReached = true;
        this.onLimit?.();
      }
    }, 200);
    this.startLevelMeter();
  }

  get elapsedSeconds() {
    return this.startedAt ? (performance.now() - this.startedAt) / 1000 : 0;
  }

  startLevelMeter() {
    if (!this.meterContext) return;
    if (this.meterContext.state === 'suspended') this.meterContext.resume().catch(() => {});
    const analyser = this.meterContext.createAnalyser();
    analyser.fftSize = 512;
    this.meterContext.createMediaStreamSource(this.stream).connect(analyser);
    const samples = new Float32Array(analyser.fftSize);
    const tick = () => {
      analyser.getFloatTimeDomainData(samples);
      let sum = 0;
      for (const sample of samples) sum += sample * sample;
      this.onLevel(Math.min(1, Math.sqrt(sum / samples.length) * 4));
      this.meterFrame = requestAnimationFrame(tick);
    };
    tick();
  }

  /**
   * Stops recording and returns the WAV recording.
   * @returns {Promise<{blob: Blob, durationSeconds: number}>}
   */
  async stop() {
    const recorder = this.mediaRecorder;
    const recorded = await new Promise((resolve) => {
      recorder.addEventListener('stop', () => resolve(new Blob(this.chunks, { type: recorder.mimeType })), { once: true });
      recorder.stop();
    });
    this.release();
    if (recorded.size === 0) {
      throw new RecorderError('empty', 'Nothing was recorded. Please try again.');
    }
    return toWav16kMono(recorded);
  }

  /** Stops without producing a recording. */
  cancel() {
    if (this.mediaRecorder?.state === 'recording') this.mediaRecorder.stop();
    this.release();
  }

  /** Releases the microphone (turns off the browser's recording indicator) and timers. */
  release() {
    clearInterval(this.timer);
    cancelAnimationFrame(this.meterFrame);
    this.meterContext?.close();
    this.stream?.getTracks().forEach((track) => track.stop());
    this.meterContext = null;
  }
}
