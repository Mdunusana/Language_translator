// FALLBACK voice input using the browser's Web Speech API (SpeechRecognition).
// Used only when the server has no speech provider, or its provider doesn't support the language.
// Note: in Chrome and Edge, this sends the audio to the browser vendor's cloud service; the UI says so.

const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;

/** LinguaVoice code → BCP-47 locale for browser recognition. Support varies by browser and is only known at runtime. */
const LOCALES = {
  en: 'en-US', af: 'af-ZA', zu: 'zu-ZA', xh: 'xh-ZA', es: 'es-ES', fr: 'fr-FR',
  pt: 'pt-BR', de: 'de-DE', zh: 'zh-CN', ar: 'ar-SA', ja: 'ja-JP', sw: 'sw-KE',
};

export function isBrowserRecognitionSupported() {
  return Boolean(Recognition);
}

const ERROR_MESSAGES = {
  'not-allowed': 'Microphone access is blocked. Allow the microphone for this site, then try again.',
  'service-not-allowed': 'Your browser does not allow speech recognition here.',
  'no-speech': "We didn't hear anything. Press the microphone and start speaking straight away.",
  'audio-capture': 'No microphone was found. Connect one and try again.',
  'network': "Your browser's speech service could not be reached. Check your connection.",
  'language-not-supported': "Your browser's speech recognition doesn't support this language.",
};

let active = null;

/**
 * Listens for one phrase and resolves with the recognised text ('' if nothing was heard).
 * @param {string} languageCode LinguaVoice code of the spoken language
 */
export function recognizeOnce(languageCode) {
  return new Promise((resolve, reject) => {
    const recognition = new Recognition();
    recognition.lang = LOCALES[languageCode] ?? languageCode;
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;
    recognition.continuous = false;

    let transcript = '';
    recognition.onresult = (event) => {
      transcript = Array.from(event.results).map((result) => result[0].transcript).join(' ').trim();
    };
    recognition.onerror = (event) => {
      active = null;
      if (event.error === 'aborted') return resolve('');
      reject(new Error(ERROR_MESSAGES[event.error] ?? 'Speech recognition failed. Please try again.'));
    };
    recognition.onend = () => {
      active = null;
      resolve(transcript);
    };
    active = recognition;
    recognition.start();
  });
}

export function stopBrowserRecognition() {
  active?.stop();
}
