// Read-aloud using the browser's built-in speech synthesis.
// This is the FALLBACK, used when the server has no text-to-speech voice for a language.
// A voice is only offered when the browser actually has one for the language.

const synth = 'speechSynthesis' in window ? window.speechSynthesis : null;

/** BCP-47 prefixes browsers use for LinguaVoice codes that differ (e.g. Mandarin voices are zh-CN). */
const VOICE_PREFIX = { zh: 'zh-cn' };

let voices = [];

function loadVoices() {
  voices = synth ? synth.getVoices() : [];
}

/** Resolves once the browser has published its voice list (it loads asynchronously in Chrome). */
export function voicesReady() {
  if (!synth) return Promise.resolve();
  loadVoices();
  if (voices.length) return Promise.resolve();
  return new Promise((resolve) => {
    const done = () => { loadVoices(); resolve(); };
    synth.addEventListener('voiceschanged', done, { once: true });
    setTimeout(done, 1500); // some browsers never fire the event when they have no voices
  });
}

/** Browser voices for a LinguaVoice language code, local (offline) voices first. */
export function browserVoicesFor(languageCode) {
  if (!synth || !languageCode) return [];
  const prefix = (VOICE_PREFIX[languageCode] ?? languageCode).toLowerCase();
  return voices
    .filter((voice) => {
      const lang = voice.lang.toLowerCase().replace('_', '-');
      return lang === prefix || lang.startsWith(`${prefix}-`);
    })
    .sort((a, b) => Number(b.localService) - Number(a.localService));
}

export function stopSpeaking() {
  synth?.cancel();
}

export function pauseSpeaking() {
  synth?.pause();
}

export function resumeSpeaking() {
  synth?.resume();
}

/** True when a pause actually took effect (or nothing is speaking any more). */
export function isSpeechPaused() {
  return !synth || synth.paused || !synth.speaking;
}

/**
 * Speaks `text`. Returns false if no browser voice is available for the language.
 * @param {{voiceURI?: string, rate?: number, onStart?: () => void, onEnd?: () => void}} [options]
 */
export function speak(text, languageCode, { voiceURI, rate = 1, onStart, onEnd } = {}) {
  const candidates = browserVoicesFor(languageCode);
  const voice = candidates.find((item) => item.voiceURI === voiceURI) ?? candidates[0];
  if (!voice) return false;
  synth.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.voice = voice;
  utterance.lang = voice.lang;
  utterance.rate = rate;
  utterance.onstart = () => onStart?.();
  utterance.onend = () => onEnd?.();
  utterance.onerror = () => onEnd?.();
  synth.speak(utterance);
  return true;
}
