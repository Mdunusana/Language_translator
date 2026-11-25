// Microphone permissions: every getUserMedia failure becomes a clear, specific message, and the microphone
// and audio resources are always released.
import { setNavigator } from './setup.mjs';
import { beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';

// Browser audio stand-ins, installed BEFORE importing the recorder (it detects AudioContext at load time).
const contexts = [];
class FakeAudioContext {
  constructor() { this.state = 'running'; this.closed = false; contexts.push(this); }
  close() { this.closed = true; return Promise.resolve(); }
  resume() { return Promise.resolve(); }
  createAnalyser() { return { fftSize: 512, getFloatTimeDomainData() {} }; }
  createMediaStreamSource() { return { connect() {} }; }
}
class FakeMediaRecorder extends EventTarget {
  constructor(stream) { super(); this.stream = stream; this.state = 'inactive'; this.mimeType = 'audio/webm'; }
  start() { this.state = 'recording'; }
  stop() { this.state = 'inactive'; this.dispatchEvent(new Event('stop')); }
}
globalThis.AudioContext = FakeAudioContext;
globalThis.MediaRecorder = FakeMediaRecorder;
globalThis.isSecureContext = true;
globalThis.requestAnimationFrame = () => 0;
globalThis.cancelAnimationFrame = () => {};

const { isRecordingSupported, RecorderError, VoiceRecorder } = await import('../js/components/recorder.js');

function micThatFailsWith(name) {
  setNavigator({ mediaDevices: { getUserMedia: async () => { throw new DOMException('fail', name); } } });
}

function fakeStream() {
  const track = { stopped: false, stop() { this.stopped = true; } };
  return { track, getTracks: () => [track] };
}

beforeEach(() => {
  contexts.length = 0;
  globalThis.isSecureContext = true;
});

describe('permission and device errors', () => {
  const cases = [
    ['NotAllowedError', 'permission-denied', /Microphone access is blocked/],
    ['SecurityError', 'permission-denied', /Microphone access is blocked/],
    ['NotFoundError', 'no-microphone', /No microphone was found/],
    ['OverconstrainedError', 'no-microphone', /No microphone was found/],
    ['NotReadableError', 'microphone-busy', /being used by another application/],
    ['TypeError', 'unknown', /could not be started/],
  ];
  for (const [domError, code, message] of cases) {
    test(`${domError} → ${code}`, async () => {
      micThatFailsWith(domError);
      await assert.rejects(new VoiceRecorder().start(), (error) => {
        assert.ok(error instanceof RecorderError);
        assert.equal(error.code, code);
        assert.match(error.message, message);
        return true;
      });
    });
  }

  test('a failed start releases the level-meter audio context', async () => {
    micThatFailsWith('NotAllowedError');
    await assert.rejects(new VoiceRecorder({ onLevel() {} }).start());
    assert.equal(contexts.length, 1);
    assert.equal(contexts[0].closed, true);
  });
});

describe('support detection', () => {
  test('recording needs a secure context (HTTPS or localhost)', async () => {
    setNavigator({ mediaDevices: { getUserMedia: async () => fakeStream() } });
    assert.equal(isRecordingSupported(), true);
    globalThis.isSecureContext = false;
    assert.equal(isRecordingSupported(), false);
    await assert.rejects(new VoiceRecorder().start(), { code: 'unsupported' });
  });
});

describe('releasing the microphone', () => {
  test('cancel stops the microphone track (turns off the browser recording indicator)', async () => {
    const stream = fakeStream();
    const recorder = new VoiceRecorder();
    await recorder.start(stream);
    assert.equal(recorder.mediaRecorder.state, 'recording');
    recorder.cancel();
    assert.equal(stream.track.stopped, true);
    assert.equal(recorder.mediaRecorder.state, 'inactive');
  });

  test('stopping with nothing recorded reports a clear error and still releases the microphone', async () => {
    const stream = fakeStream();
    const recorder = new VoiceRecorder();
    await recorder.start(stream);
    await assert.rejects(recorder.stop(), { code: 'empty', message: 'Nothing was recorded. Please try again.' });
    assert.equal(stream.track.stopped, true);
  });
});
