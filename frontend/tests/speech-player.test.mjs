// Audio playback: server MP3 playback with pause/resume/stop, caching, autoplay blocking, and the browser
// speech-synthesis fallback, including the late-'start' race that was fixed in Phase 5.
import { jsonResponse, storeToken, wait } from './setup.mjs';
import { beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';

// --- browser stand-ins, installed before importing the player ---
class FakeAudio extends EventTarget {
  static blockPlayback = false;
  static plays = 0;
  constructor() { super(); this.src = ''; this.currentTime = 0; this.paused = true; }
  async play() {
    FakeAudio.plays += 1;
    if (FakeAudio.blockPlayback) throw new DOMException('blocked', 'NotAllowedError');
    this.paused = false;
    queueMicrotask(() => this.dispatchEvent(new Event('playing')));
  }
  pause() { this.paused = true; }
}
const synth = {
  speaking: false, paused: false, canPause: true, utterances: [],
  getVoices: () => [{ lang: 'fr-FR', name: 'Test French', voiceURI: 'fr-1', localService: true }],
  speak(utterance) { this.utterances.push(utterance); this.speaking = true; },
  cancel() { this.speaking = false; this.paused = false; },
  pause() { if (this.canPause) this.paused = true; },
  resume() { this.paused = false; },
  addEventListener() {},
};
globalThis.Audio = FakeAudio;
globalThis.speechSynthesis = synth;
globalThis.SpeechSynthesisUtterance = class { constructor(text) { this.text = text; } };

const { SpeechPlayer } = await import('../js/components/speech-player.js');
const { voicesReady } = await import('../js/components/browser-speech.js');
await voicesReady();

let fetches;
function serveAudio({ delay = 0, status = 200 } = {}) {
  fetches = 0;
  globalThis.fetch = async () => {
    fetches += 1;
    if (delay) await wait(delay);
    if (status !== 200) return jsonResponse(status, { status, message: 'This voice isn\'t available for isiZulu.', fieldErrors: { voice: 'This voice isn\'t available for isiZulu.' } });
    return new Response(new Blob([new Uint8Array([0xff, 0xfb])]), { status: 200, headers: { 'Content-Type': 'audio/mpeg' } });
  };
}

function makePlayer() {
  const states = [];
  const errors = [];
  const player = new SpeechPlayer({ onStateChange: (s) => states.push(s), onError: (e) => errors.push(e.message) });
  return { player, states, errors };
}

const SERVER = { mode: 'server', text: 'Sawubona', language: 'zu', voice: 'zu-ZA-ThandoNeural', speed: 1 };
const BROWSER = { mode: 'browser', text: 'Bonjour', language: 'fr', speed: 1 };

beforeEach(() => {
  storeToken();
  FakeAudio.blockPlayback = false;
  FakeAudio.plays = 0;
  Object.assign(synth, { speaking: false, paused: false, canPause: true, utterances: [] });
});

describe('server voices', () => {
  test('loading → playing → paused → playing → idle', async () => {
    serveAudio();
    const { player, states } = makePlayer();
    await player.play(SERVER);
    await wait(0);
    assert.equal(player.state, 'playing');
    player.pause();
    assert.equal(player.state, 'paused');
    assert.equal(player.audio.paused, true);
    player.resume();
    assert.equal(player.state, 'playing');
    player.audio.dispatchEvent(new Event('ended'));
    assert.deepEqual(states, ['loading', 'playing', 'paused', 'playing', 'idle']);
  });

  test('replaying the same text, voice and speed uses the cached clip (no second request)', async () => {
    serveAudio();
    const { player } = makePlayer();
    await player.play(SERVER);
    player.stop();
    await player.play(SERVER);
    assert.equal(fetches, 1);
    await player.play({ ...SERVER, speed: 1.5 });
    assert.equal(fetches, 2, 'a different speed is a different clip');
  });

  test('stopping while the audio is still loading never starts playback', async () => {
    serveAudio({ delay: 30 });
    const { player } = makePlayer();
    const playing = player.play(SERVER);
    player.stop();
    await playing;
    assert.equal(player.state, 'idle');
    assert.equal(FakeAudio.plays, 0);
  });

  test('blocked autoplay gives a clear instruction and returns to idle', async () => {
    serveAudio();
    FakeAudio.blockPlayback = true;
    const { player } = makePlayer();
    await assert.rejects(player.play(SERVER), { message: 'Your browser blocked playback. Press Listen again to play.' });
    assert.equal(player.state, 'idle');
  });

  test('an API error (e.g. unavailable voice) is passed on with its field message', async () => {
    serveAudio({ status: 400 });
    const { player } = makePlayer();
    await assert.rejects(player.play(SERVER), (error) => {
      assert.equal(error.fieldErrors.voice, 'This voice isn\'t available for isiZulu.');
      return true;
    });
    assert.equal(player.state, 'idle');
  });
});

describe('browser voices (fallback)', () => {
  test('a late "start" event cannot override the user\'s pause', async () => {
    const { player } = makePlayer();
    await player.play(BROWSER);
    assert.equal(player.state, 'loading', 'waits for the voice to really start');
    const utterance = synth.utterances[0];
    utterance.onstart();
    assert.equal(player.state, 'playing');
    player.pause();
    assert.equal(player.state, 'paused');
    utterance.onstart(); // some voices fire start late or twice
    assert.equal(player.state, 'paused');
    player.resume();
    assert.equal(player.state, 'playing');
    utterance.onend();
    assert.equal(player.state, 'idle');
  });

  test('a voice that cannot pause is stopped cleanly with an explanation', async () => {
    synth.canPause = false;
    const { player, errors } = makePlayer();
    await player.play(BROWSER);
    synth.utterances[0].onstart();
    player.pause();
    await wait(350);
    assert.equal(player.state, 'idle');
    assert.match(errors[0], /can't be paused/);
  });

  test('a language with no browser voice fails with a clear message', async () => {
    const { player } = makePlayer();
    await assert.rejects(player.play({ ...BROWSER, language: 'xh' }), { message: /No voice is available/ });
    assert.equal(player.state, 'idle');
  });
});
