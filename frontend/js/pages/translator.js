import { api } from '../api/client.js';
import { requireAuth, session } from '../auth/session.js';
import { renderAppShell } from '../components/app-shell.js';
import { isBrowserRecognitionSupported, recognizeOnce, stopBrowserRecognition } from '../components/browser-recognition.js';
import { browserVoicesFor, voicesReady } from '../components/browser-speech.js';
import { isRecordingSupported, VoiceRecorder } from '../components/recorder.js';
import { hideAlert, showAlert, withBusyButton } from '../components/forms.js';
import { SpeechPlayer } from '../components/speech-player.js';
import { showToast } from '../components/toast.js';

const MAX_LENGTH = 5000;
const AUTO = 'auto';
const LAST_LANGUAGES_KEY = 'linguavoice.lastLanguages';
const SPEED_KEY = 'linguavoice.speechSpeed';
const VOICE_KEY_PREFIX = 'linguavoice.voice.';

const root = document.querySelector('[data-translator]');
const els = {
  source: document.getElementById('sourceLanguage'),
  target: document.getElementById('targetLanguage'),
  input: document.getElementById('sourceText'),
  count: root.querySelector('[data-count]'),
  countBox: document.getElementById('char-count'),
  clear: root.querySelector('[data-clear]'),
  swap: root.querySelector('[data-swap]'),
  translate: root.querySelector('[data-translate]'),
  outputPanel: root.querySelector('.panel-output'),
  output: root.querySelector('[data-output]'),
  outputStatus: root.querySelector('[data-output-status]'),
  detected: root.querySelector('[data-detected]'),
  copy: root.querySelector('[data-copy]'),
  listen: root.querySelector('[data-listen]'),
  stop: root.querySelector('[data-stop]'),
  voiceSelect: root.querySelector('[data-voice-select]'),
  speedSelect: root.querySelector('[data-speed-select]'),
  unavailable: document.querySelector('[data-unavailable]'),
  provider: document.querySelector('[data-provider]'),
  inputPanel: root.querySelector('.panel-input'),
  mic: root.querySelector('[data-mic]'),
  voiceOverlay: root.querySelector('[data-voice-overlay]'),
  voiceLabel: root.querySelector('[data-voice-label]'),
  voiceTimer: root.querySelector('[data-voice-timer]'),
  voiceStop: root.querySelector('[data-voice-stop]'),
  voiceCancel: root.querySelector('[data-voice-cancel]'),
  levelMeter: root.querySelector('.level-meter'),
};

const state = {
  languages: [],
  /** Last successful translation; null when the output panel is empty. */
  result: null,
  /** Active VoiceRecorder while recording with the server-side recogniser. */
  recorder: null,
  /** 'idle' | 'recording' | 'listening-browser' | 'processing' */
  voiceState: 'idle',
  /** How the current translation is read aloud: 'server' | 'browser' | null (no voice available). */
  speechMode: null,
  /** Server voice lists per language code, fetched once per page load. */
  serverVoices: new Map(),
  serverVoiceProvider: null,
};

const player = new SpeechPlayer({
  onStateChange: updatePlayButton,
  onError: (error) => showToast(error.message, 'error', 4000),
});

const MAX_RECORDING_SECONDS = 60;

/* ---------- helpers ---------- */

function languageName(code) {
  const known = state.languages.find((language) => language.code === code);
  if (known) return known.name;
  try { return new Intl.DisplayNames(['en'], { type: 'language' }).of(code) ?? code; } catch { return code; }
}

function readLastLanguages() {
  try { return JSON.parse(localStorage.getItem(LAST_LANGUAGES_KEY) ?? 'null'); } catch { return null; }
}

function rememberLanguages() {
  try {
    localStorage.setItem(LAST_LANGUAGES_KEY, JSON.stringify({ source: els.source.value, target: els.target.value }));
  } catch { /* storage unavailable: not important */ }
}

function isOffered(code) {
  return state.languages.some((language) => language.code === code);
}

function fillSelect(select, languages) {
  for (const language of languages) {
    const option = document.createElement('option');
    option.value = language.code;
    option.textContent = language.nativeName && language.nativeName !== language.name
      ? `${language.name} · ${language.nativeName}`
      : language.name;
    select.append(option);
  }
}

/** Initial languages: last used on this device → account preferences → English to the first other language. */
function chooseInitialLanguages() {
  const last = readLastLanguages();
  const prefs = session.user()?.preferences;
  let source = last?.source ?? prefs?.preferredSourceLanguage ?? 'en';
  let target = last?.target ?? prefs?.preferredTargetLanguage;

  if (source !== AUTO && !isOffered(source)) source = AUTO;
  if (!isOffered(target) || target === source) {
    target = state.languages.find((language) => language.code !== source)?.code;
  }
  els.source.value = source;
  els.target.value = target;
}

function setControlsEnabled(enabled) {
  [els.source, els.target, els.input, els.swap, els.translate].forEach((el) => { el.disabled = !enabled; });
}

/* ---------- output panel ---------- */

function clearOutput() {
  player.stop();
  state.result = null;
  els.output.replaceChildren(Object.assign(document.createElement('span'), {
    className: 'placeholder', textContent: 'Translation will appear here',
  }));
  els.outputPanel.dataset.state = 'empty';
  els.detected.hidden = true;
  els.outputStatus.textContent = '';
  els.copy.disabled = true;
  prepareSpeech(null);
}

function renderResult(result, statusText = 'Saved to your history') {
  state.result = result;
  els.output.textContent = result.translatedText; // textContent: provider output is never parsed as HTML
  els.outputPanel.dataset.state = 'ready';
  els.copy.disabled = false;
  if (result.sourceDetected) {
    els.detected.textContent = result.sourceLanguage === 'und'
      ? 'Language not detected'
      : `Detected: ${languageName(result.sourceLanguage)}`;
    els.detected.hidden = false;
  } else {
    els.detected.hidden = true;
  }
  els.outputStatus.textContent = statusText;
  prepareSpeech(result.targetLanguage);
}

/* ---------- text-to-speech ---------- */

function readStored(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}

function store(key, value) {
  try { localStorage.setItem(key, value); } catch { /* not important */ }
}

/** Speed: last choice on this device → account preference → 1×, snapped to the nearest option. */
function initSpeed() {
  const preferred = Number(readStored(SPEED_KEY) ?? session.user()?.preferences?.speechSpeed ?? 1);
  const options = [...els.speedSelect.options].map((option) => Number(option.value));
  const nearest = options.reduce((best, value) => (Math.abs(value - preferred) < Math.abs(best - preferred) ? value : best), 1);
  els.speedSelect.value = String(nearest);
}

async function serverVoicesFor(code) {
  if (!state.serverVoices.has(code)) {
    const response = await api.voices(code);
    state.serverVoices.set(code, response.available ? response.voices : []);
    if (response.provider) state.serverVoiceProvider = response.provider;
  }
  return state.serverVoices.get(code);
}

/**
 * Chooses how to read `code` aloud and fills the voice list:
 * the server's voices when it has them, otherwise the browser's, otherwise nothing (button disabled).
 */
async function prepareSpeech(code) {
  player.stop();
  els.voiceSelect.replaceChildren();
  state.speechMode = null;
  if (!code) {
    updatePlayButton('idle');
    return;
  }

  let options = [];
  const language = state.languages.find((item) => item.code === code);
  if (language?.voiceOutput) {
    try {
      const voices = await serverVoicesFor(code);
      if (voices.length) {
        state.speechMode = 'server';
        options = voices.map((voice) => ({ value: voice.id, label: `${voice.name} · ${voice.gender} · ${voice.locale}` }));
      }
    } catch {
      // Server voices unavailable right now: fall through to the browser's voices.
    }
  }
  if (!state.speechMode) {
    await voicesReady();
    const voices = browserVoicesFor(code);
    if (voices.length) {
      state.speechMode = 'browser';
      options = voices.map((voice) => ({ value: voice.voiceURI, label: `${voice.name} (browser)` }));
    }
  }
  if (state.result?.targetLanguage !== code) return; // the result changed while voices were loading

  for (const { value, label } of options) {
    els.voiceSelect.append(Object.assign(document.createElement('option'), { value, textContent: label }));
  }
  if (!options.length) {
    els.voiceSelect.append(Object.assign(document.createElement('option'), { value: '', textContent: 'No voice available' }));
  }
  const remembered = readStored(VOICE_KEY_PREFIX + code);
  if (remembered && options.some((option) => option.value === remembered)) els.voiceSelect.value = remembered;
  updatePlayButton(player.state);
}

function updatePlayButton(playerState) {
  const name = state.result ? languageName(state.result.targetLanguage) : '';
  const available = Boolean(state.result && state.speechMode);
  els.listen.dataset.state = playerState;
  els.listen.disabled = !available;
  els.voiceSelect.disabled = !available || playerState === 'loading';
  els.speedSelect.disabled = !available;
  els.stop.hidden = playerState === 'idle';

  const source = state.speechMode === 'server'
    ? `voice by ${state.serverVoiceProvider ?? 'the server'}`
    : 'your browser’s voice';
  const label = !state.result ? 'Listen to translation'
    : !available ? `No ${name} voice is available`
    : { idle: `Listen in ${name} (${source})`, loading: 'Preparing audio… (press to cancel)', playing: 'Pause', paused: 'Resume' }[playerState];
  els.listen.title = label;
  els.listen.setAttribute('aria-label', label);
}

async function onListenClick() {
  if (!state.result || !state.speechMode) return;
  switch (player.state) {
    case 'playing': player.pause(); return;
    case 'paused': player.resume(); return;
    case 'loading': player.stop(); return;
    default:
      try {
        await player.play({
          mode: state.speechMode,
          text: state.result.translatedText,
          language: state.result.targetLanguage,
          voice: els.voiceSelect.value || undefined,
          speed: Number(els.speedSelect.value),
        });
      } catch (error) {
        const fieldMessage = error.fieldErrors ? Object.values(error.fieldErrors)[0] : null;
        showToast(fieldMessage ?? error.message, 'error', 4500);
      }
  }
}

/* ---------- input ---------- */

function updateCount() {
  const length = els.input.value.length;
  els.count.textContent = length.toLocaleString();
  els.countBox.dataset.level = length >= MAX_LENGTH ? 'full' : length >= MAX_LENGTH * 0.9 ? 'warn' : '';
  els.clear.hidden = length === 0;
}

/* ---------- actions ---------- */

async function translate() {
  hideAlert(root);
  const text = els.input.value.trim();
  if (!text) {
    showAlert(root, 'Enter some text to translate.');
    els.input.focus();
    return;
  }
  if (els.source.value === els.target.value) {
    showAlert(root, 'Choose a target language that is different from the source language.');
    els.target.focus();
    return;
  }

  player.stop();
  els.outputPanel.dataset.state = 'loading';
  els.outputStatus.textContent = 'Translating…';
  els.detected.hidden = true;

  await withBusyButton(els.translate, 'Translating…', async () => {
    try {
      const result = await api.translateText({
        text,
        sourceLanguage: els.source.value,
        targetLanguage: els.target.value,
      });
      renderResult(result);
    } catch (error) {
      clearOutput();
      const fieldMessage = Object.values(error.fieldErrors)[0];
      showAlert(root, fieldMessage ?? error.message);
    }
  });
}

function swapLanguages() {
  let source = els.source.value;
  if (source === AUTO) {
    const detected = state.result?.sourceDetected ? state.result.sourceLanguage : null;
    if (!detected || !isOffered(detected)) {
      showToast('Choose a source language first, or translate once so it can be detected.', 'info', 3500);
      return;
    }
    source = detected;
  }
  const previousOutput = state.result?.translatedText;
  els.source.value = els.target.value;
  els.target.value = source;
  rememberLanguages();
  updateMicButton();

  if (previousOutput) {
    els.input.value = previousOutput;
    updateCount();
    clearOutput();
  }
  els.input.focus();
}

async function copyTranslation() {
  if (!state.result) return;
  try {
    await navigator.clipboard.writeText(state.result.translatedText);
    showToast('Translation copied to clipboard');
  } catch {
    showToast('Could not copy. Select the text and copy it manually.', 'error');
  }
}

/* ---------- voice input ---------- */

/**
 * How voice input works for the selected source language:
 *  'server'          recorded here, recognised by the server's speech provider (primary)
 *  'browser'         recognised by the browser's own Web Speech API (fallback)
 *  'needs-language'  "Detect language" is selected; speech recognition needs the spoken language up front
 *  'unavailable'     neither is possible
 */
function voiceMode(code) {
  if (code === AUTO) return 'needs-language';
  const language = state.languages.find((item) => item.code === code);
  if (language?.voiceInput && isRecordingSupported()) return 'server';
  if (isBrowserRecognitionSupported()) return 'browser';
  return 'unavailable';
}

function updateMicButton() {
  const code = els.source.value;
  const mode = state.languages.length ? voiceMode(code) : 'unavailable';
  els.mic.dataset.mode = mode;
  els.mic.disabled = mode === 'unavailable' || state.voiceState !== 'idle';
  const name = languageName(code);
  els.mic.title = {
    server: `Speak in ${name} (up to 1 minute)`,
    browser: `Speak in ${name}. Uses your browser's speech recognition, which may send audio to your browser's provider.`,
    'needs-language': 'Choose the language you will speak in the "From" list',
    unavailable: `Voice input isn't available for ${name} in this browser`,
  }[mode];
  els.mic.setAttribute('aria-label', els.mic.title);
}

function setVoiceState(voiceState, label = '') {
  state.voiceState = voiceState;
  els.inputPanel.dataset.voiceState = voiceState;
  els.voiceOverlay.hidden = voiceState === 'idle';
  els.voiceLabel.textContent = label;
  const busy = voiceState !== 'idle';
  [els.input, els.translate, els.swap, els.source, els.target].forEach((el) => { el.disabled = busy; });
  updateMicButton();
}

function formatSeconds(seconds) {
  const whole = Math.floor(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

async function onMicClick() {
  hideAlert(root);
  const mode = voiceMode(els.source.value);
  if (mode === 'needs-language') {
    showToast('Choose the language you will speak in the "From" list first.', 'info', 3500);
    els.source.focus();
    return;
  }
  if (els.source.value === els.target.value) {
    showAlert(root, 'Choose a target language that is different from the language you will speak.');
    return;
  }
  player.stop();
  if (mode === 'server') await startServerRecording();
  else if (mode === 'browser') await startBrowserRecognition();
}

async function startServerRecording() {
  const recorder = new VoiceRecorder({
    maxSeconds: MAX_RECORDING_SECONDS,
    onTick: (seconds) => { els.voiceTimer.textContent = formatSeconds(Math.min(seconds, MAX_RECORDING_SECONDS)); },
    onLevel: (level) => els.levelMeter.style.setProperty('--level', level.toFixed(3)),
    onLimit: () => {
      showToast('Maximum length reached. Translating your recording…', 'info');
      finishServerRecording();
    },
  });
  els.voiceTimer.textContent = '0:00';
  setVoiceState('recording', 'Waiting for microphone permission…');
  try {
    await recorder.start();
  } catch (error) {
    setVoiceState('idle');
    showAlert(root, error.message);
    return;
  }
  state.recorder = recorder;
  els.voiceLabel.textContent = `Listening in ${languageName(els.source.value)}…`;
  els.voiceStop.focus();
}

async function finishServerRecording() {
  const recorder = state.recorder;
  if (!recorder || state.voiceState !== 'recording') return;
  state.recorder = null;
  setVoiceState('processing', 'Processing your recording…');

  try {
    const { blob, durationSeconds } = await recorder.stop();
    if (durationSeconds < 0.5) {
      throw new Error('That was too short. Hold on a moment longer and speak your phrase.');
    }
    els.voiceLabel.textContent = 'Recognising speech and translating…';
    els.outputPanel.dataset.state = 'loading';

    const form = new FormData();
    form.append('audio', blob, 'recording.wav');
    form.append('sourceLanguage', els.source.value);
    form.append('targetLanguage', els.target.value);
    const result = await api.translateVoice(form);

    els.input.value = result.originalText;
    updateCount();
    renderResult(result, 'Voice translation saved to your history');
  } catch (error) {
    clearOutput();
    const fieldMessage = error.fieldErrors ? Object.values(error.fieldErrors)[0] : null;
    showAlert(root, fieldMessage ?? error.message);
  } finally {
    setVoiceState('idle');
  }
}

function cancelVoice() {
  if (state.voiceState === 'recording') {
    state.recorder?.cancel();
    state.recorder = null;
    setVoiceState('idle');
    showToast('Recording cancelled', 'info');
  } else if (state.voiceState === 'listening-browser') {
    stopBrowserRecognition();
  }
}

async function startBrowserRecognition() {
  setVoiceState('listening-browser', `Listening in ${languageName(els.source.value)} (browser speech recognition)…`);
  let transcript;
  try {
    transcript = await recognizeOnce(els.source.value);
  } catch (error) {
    setVoiceState('idle');
    showAlert(root, error.message);
    return;
  }
  setVoiceState('idle');
  if (!transcript) {
    showAlert(root, "We didn't catch anything. Press the microphone and try again.");
    return;
  }
  els.input.value = transcript;
  updateCount();
  await translate();
  if (state.result) els.outputStatus.textContent = 'Recognised by your browser · saved to your history';
}

/* ---------- start-up ---------- */

function bindEvents() {
  els.input.addEventListener('input', () => {
    updateCount();
    if (state.result) els.outputStatus.textContent = 'Text changed: translate again to update';
  });
  els.input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      translate();
    }
  });
  els.clear.addEventListener('click', () => {
    els.input.value = '';
    updateCount();
    clearOutput();
    hideAlert(root);
    els.input.focus();
  });
  els.source.addEventListener('change', () => {
    rememberLanguages();
    updateMicButton();
  });
  els.mic.addEventListener('click', onMicClick);
  els.voiceStop.addEventListener('click', () => {
    if (state.voiceState === 'recording') finishServerRecording();
    else if (state.voiceState === 'listening-browser') stopBrowserRecognition();
  });
  els.voiceCancel.addEventListener('click', cancelVoice);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && state.voiceState !== 'idle' && state.voiceState !== 'processing') cancelVoice();
  });
  // Release the microphone if the user leaves mid-recording.
  window.addEventListener('pagehide', () => state.recorder?.cancel());
  els.target.addEventListener('change', () => {
    rememberLanguages();
    if (state.result) els.outputStatus.textContent = 'Language changed: translate again to update';
  });
  els.swap.addEventListener('click', swapLanguages);
  els.translate.addEventListener('click', translate);
  els.copy.addEventListener('click', copyTranslation);
  els.listen.addEventListener('click', onListenClick);
  els.stop.addEventListener('click', () => player.stop());
  els.voiceSelect.addEventListener('change', () => {
    player.stop();
    if (state.result) store(VOICE_KEY_PREFIX + state.result.targetLanguage, els.voiceSelect.value);
  });
  els.speedSelect.addEventListener('change', () => {
    player.stop();
    store(SPEED_KEY, els.speedSelect.value);
  });
  window.addEventListener('pagehide', () => player.stop());
}

async function init() {
  bindEvents();
  updateCount();
  initSpeed();
  updatePlayButton('idle');

  let catalog;
  try {
    catalog = await api.languages();
  } catch (error) {
    if (error.status !== 401) {
      els.unavailable.dataset.type = 'error';
      showUnavailable(`Could not load the language list. ${error.message}`);
    }
    return;
  }

  if (!catalog.translationAvailable) {
    showUnavailable(catalog.message);
    return;
  }

  state.languages = catalog.languages;
  fillSelect(els.source, state.languages);
  fillSelect(els.target, state.languages);
  chooseInitialLanguages();
  els.provider.textContent = catalog.voiceInputProvider
    ? `Translations by ${catalog.provider} · Speech by ${catalog.voiceInputProvider}`
    : `Translations by ${catalog.provider}`;
  els.provider.hidden = false;
  setControlsEnabled(true);
  updateMicButton();
  els.input.focus();
}

function showUnavailable(message) {
  els.unavailable.textContent = message;
  els.unavailable.hidden = false;
  setControlsEnabled(false);
}

if (requireAuth()) {
  renderAppShell();
  init();
}
