import { api } from '../api/client.js';
import { requireAuth, session } from '../auth/session.js';
import { renderAppShell } from '../components/app-shell.js';
import { findVoice, speak, stopSpeaking, voicesReady } from '../components/browser-speech.js';
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
};

const state = {
  languages: [],
  /** Last successful translation; null when the output panel is empty. */
  result: null,
};

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

function renderResult(result) {
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
  els.outputStatus.textContent = 'Saved to your history';
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
  els.source.addEventListener('change', rememberLanguages);
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
  els.provider.textContent = `Translations by ${catalog.provider}`;
  els.provider.hidden = false;
  setControlsEnabled(true);
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
