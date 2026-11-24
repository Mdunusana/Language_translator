// Read-aloud using the browser's built-in speech synthesis.
// This is the documented FALLBACK; Phase 5 adds server-side text-to-speech as the primary engine.
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

export function findVoice(languageCode) {
  if (!synth || !languageCode) return null;
  const prefix = (VOICE_PREFIX[languageCode] ?? languageCode).toLowerCase();
  const matches = voices.filter((voice) => voice.lang.toLowerCase().replace('_', '-').startsWith(prefix));
  return matches.find((voice) => voice.localService) ?? matches[0] ?? null;
}

export function isSpeaking() {
  return Boolean(synth?.speaking);
}

export function stopSpeaking() {
  synth?.cancel();
}

/**
 * Speaks `text` in `languageCode`. Returns false if no voice is available.
 * @param {{onEnd?: () => void}} [handlers]
 */
export function speak(text, languageCode, { onEnd } = {}) {
  const voice = findVoice(languageCode);
  if (!voice) return false;
  synth.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.voice = voice;
  utterance.lang = voice.lang;
  utterance.onend = () => onEnd?.();
  utterance.onerror = () => onEnd?.();
  synth.speak(utterance);
  return true;
}
