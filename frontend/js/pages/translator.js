import { api } from '../api/client.js';
import { requireAuth, session } from '../auth/session.js';
import { renderAppShell } from '../components/app-shell.js';
import { isBrowserRecognitionSupported, recognizeOnce, stopBrowserRecognition } from '../components/browser-recognition.js';
import { findVoice, speak, stopSpeaking, voicesReady } from '../components/browser-speech.js';
import { isRecordingSupported, VoiceRecorder } from '../components/recorder.js';
import { hideAlert, showAlert, withBusyButton } from '../components/forms.js';
import { showToast } from '../components/toast.js';

const MAX_LENGTH = 5000;
const AUTO = 'auto';
const LAST_LANGUAGES_KEY = 'linguavoice.lastLanguages';

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
};

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
  stopSpeaking();
  state.result = null;
  els.output.replaceChildren(Object.assign(document.createElement('span'), {
    className: 'placeholder', textContent: 'Translation will appear here',
  }));
  els.outputPanel.dataset.state = 'empty';
  els.detected.hidden = true;
  els.outputStatus.textContent = '';
  els.copy.disabled = true;
  updateListenButton();
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
  updateListenButton();
}

function updateListenButton() {
  els.listen.setAttribute('aria-pressed', 'false');
  if (!state.result) {
    els.listen.disabled = true;
    els.listen.title = 'Listen';
    return;
  }
  const hasVoice = Boolean(findVoice(state.result.targetLanguage));
  els.listen.disabled = !hasVoice;
  els.listen.title = hasVoice
    ? 'Listen (uses your browser’s voice)'
    : `Your browser has no ${languageName(state.result.targetLanguage)} voice`;
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

  stopSpeaking();
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

function toggleListen() {
  if (!state.result) return;
  if (els.listen.getAttribute('aria-pressed') === 'true') {
    stopSpeaking();
    els.listen.setAttribute('aria-pressed', 'false');
    return;
  }
  const started = speak(state.result.translatedText, state.result.targetLanguage, {
    onEnd: () => els.listen.setAttribute('aria-pressed', 'false'),
  });
  if (started) {
    els.listen.setAttribute('aria-pressed', 'true');
  } else {
    showToast(`Your browser has no ${languageName(state.result.targetLanguage)} voice.`, 'error');
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
  stopSpeaking();
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
  els.listen.addEventListener('click', toggleListen);
}

async function init() {
  bindEvents();
  updateCount();
  voicesReady().then(updateListenButton);

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
