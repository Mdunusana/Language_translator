// Plays translations aloud.
//  - 'server' mode (primary): MP3 from POST /api/speech/synthesize, played through an <audio> element,
//    which gives reliable pause / resume / stop in every browser.
//  - 'browser' mode (fallback): the browser's own speech synthesis.
// Generated audio is kept only in memory (a few recent clips, as blob URLs) so replaying is instant.

import { api } from '../api/client.js';
import { isSpeechPaused, pauseSpeaking, resumeSpeaking, speak, stopSpeaking } from './browser-speech.js';

const MAX_CACHED_CLIPS = 8;

export class SpeechPlayer {
  /**
   * @param {object} handlers
   * @param {(state: 'idle'|'loading'|'playing'|'paused') => void} handlers.onStateChange
   * @param {(error: Error) => void} handlers.onError
   */
  constructor({ onStateChange, onError }) {
    this.onStateChange = onStateChange;
    this.onError = onError;
    this.state = 'idle';
    this.mode = null;
    this.request = 0; // increments on every play/stop so stale async results are ignored
    this.clips = new Map();
    this.audio = new Audio();
    this.audio.addEventListener('playing', () => this.setState('playing'));
    this.audio.addEventListener('ended', () => this.setState('idle'));
    this.audio.addEventListener('error', () => {
      if (this.mode === 'server' && this.state !== 'idle') {
        this.setState('idle');
        this.onError(new Error('The audio could not be played.'));
      }
    });
  }

  setState(state) {
    this.state = state;
    this.onStateChange(state);
  }

  /**
   * @param {{mode: 'server'|'browser', text: string, language: string, voice?: string, speed: number}} options
   */
  async play({ mode, text, language, voice, speed }) {
    this.stop();
    const request = ++this.request;
    this.mode = mode;

    if (mode === 'browser') {
      // Some voices fire 'start' late (after the user may already have paused). Only ever move
      // loading → playing, never paused → playing, so a late event can't override the user's pause.
      const markPlaying = () => {
        if (request === this.request && this.state === 'loading') this.setState('playing');
      };
      this.setState('loading');
      const started = speak(text, language, {
        voiceURI: voice,
        rate: speed,
        onStart: markPlaying,
        onEnd: () => { if (request === this.request) this.setState('idle'); },
      });
      if (!started) {
        this.setState('idle');
        throw new Error('No voice is available for this language in your browser.');
      }
      setTimeout(markPlaying, 1500); // some browsers never fire 'start'
      return;
    }

    this.setState('loading');
    const key = JSON.stringify([text, language, voice ?? '', speed]);
    let url = this.clips.get(key);
    try {
      if (!url) {
        const blob = await api.synthesize({ text, language, voice: voice || null, speed });
        url = URL.createObjectURL(blob);
        this.remember(key, url);
      }
      if (request !== this.request) return; // stopped while loading
      this.audio.src = url;
      await this.audio.play();
    } catch (error) {
      if (request !== this.request) return;
      this.setState('idle');
      if (error.name === 'NotAllowedError') {
        // Some browsers (notably Safari) only allow playback directly inside a click. The clip is cached now,
        // so a second press plays it immediately.
        throw new Error('Your browser blocked playback. Press Listen again to play.');
      }
      throw error;
    }
  }

  pause() {
    if (this.state !== 'playing') return;
    if (this.mode === 'server') {
      this.audio.pause();
      this.setState('paused');
      return;
    }
    // Browser speech synthesis can't pause some voices (e.g. several in Chrome): it keeps talking.
    // Check that the pause took effect; if not, stop cleanly instead of showing a misleading "paused" state.
    pauseSpeaking();
    this.setState('paused');
    const request = this.request;
    setTimeout(() => {
      if (request === this.request && this.state === 'paused' && !isSpeechPaused()) {
        this.stop();
        this.onError(new Error("Your browser's voice can't be paused, so playback was stopped. Press Listen to start again."));
      }
    }, 300);
  }

  resume() {
    if (this.state !== 'paused') return;
    if (this.mode === 'server') this.audio.play().catch(() => this.setState('idle'));
    else resumeSpeaking();
    this.setState('playing');
  }

  stop() {
    this.request += 1;
    this.audio.pause();
    if (this.audio.src) this.audio.currentTime = 0;
    stopSpeaking();
    if (this.state !== 'idle') this.setState('idle');
  }

  remember(key, url) {
    this.clips.set(key, url);
    if (this.clips.size > MAX_CACHED_CLIPS) {
      const [oldestKey, oldestUrl] = this.clips.entries().next().value;
      this.clips.delete(oldestKey);
      URL.revokeObjectURL(oldestUrl);
    }
  }
}
