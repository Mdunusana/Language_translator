import { api } from '../api/client.js';
import { requireAuth } from '../auth/session.js';
import { renderAppShell } from '../components/app-shell.js';
import { debounce, formatCount, fullDateTime, timeAgo } from '../components/format.js';
import { hideAlert, showAlert, withBusyButton } from '../components/forms.js';
import { fillLanguageOptions, languageName } from '../components/languages.js';
import { showToast } from '../components/toast.js';

const PAGE_SIZE = 20;

const els = {
  filters: document.querySelector('[data-filters]'),
  search: document.getElementById('search'),
  language: document.getElementById('language'),
  list: document.querySelector('[data-history-list]'),
  count: document.querySelector('[data-result-count]'),
  empty: document.querySelector('[data-empty]'),
  emptyTitle: document.querySelector('[data-empty-title]'),
  emptyText: document.querySelector('[data-empty-text]'),
  emptyAction: document.querySelector('[data-empty-action]'),
  loadMore: document.querySelector('[data-load-more]'),
  clearAll: document.querySelector('[data-clear-all]'),
  dialog: document.querySelector('[data-confirm-dialog]'),
  dialogCount: document.querySelector('[data-confirm-count]'),
  alertHost: document.querySelector('.container'),
};

const state = { page: 0, totalItems: 0, totalPages: 0, loaded: 0, request: 0, anyHistory: false };

const ICONS = {
  arrow: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></svg>',
  text: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M4 7V5h16v2"/><path d="M12 5v14"/><path d="M9 19h6"/></svg>',
  voice: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z"/><path d="M19 11a7 7 0 0 1-14 0"/></svg>',
  copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/></svg>',
};

function currentFilters() {
  const data = new FormData(els.filters);
  return {
    search: String(data.get('search') ?? '').trim(),
    language: String(data.get('language') ?? ''),
    type: String(data.get('type') ?? ''),
  };
}

function hasFilters(filters) {
  return Boolean(filters.search || filters.language || filters.type);
}

/** Puts `text` into `parent`, wrapping case-insensitive matches of `term` in <mark>. Never uses innerHTML. */
function appendHighlighted(parent, text, term) {
  if (!term) {
    parent.textContent = text;
    return;
  }
  const lower = text.toLowerCase();
  const needle = term.toLowerCase();
  let from = 0;
  let index = lower.indexOf(needle);
  while (index !== -1) {
    parent.append(text.slice(from, index), Object.assign(document.createElement('mark'), { textContent: text.slice(index, index + needle.length) }));
    from = index + needle.length;
    index = lower.indexOf(needle, from);
  }
  parent.append(text.slice(from));
}

function renderItem(item, term) {
  const li = document.createElement('li');
  li.className = 'history-item';
  li.dataset.id = item.id;

  const meta = document.createElement('div');
  meta.className = 'history-meta';
  const pair = document.createElement('span');
  pair.className = 'lang-pair';
  pair.append(languageName(item.sourceLanguage));
  pair.insertAdjacentHTML('beforeend', ICONS.arrow); // static icon markup only
  pair.append(languageName(item.targetLanguage));
  const badge = document.createElement('span');
  badge.className = 'type-badge';
  badge.dataset.type = item.translationType;
  badge.insertAdjacentHTML('beforeend', item.translationType === 'VOICE' ? ICONS.voice : ICONS.text);
  badge.append(item.translationType === 'VOICE' ? 'Voice' : 'Text');
  const time = document.createElement('time');
  time.dateTime = item.createdAt;
  time.title = fullDateTime(item.createdAt);
  time.textContent = timeAgo(item.createdAt);
  meta.append(pair, badge, time);

  const texts = document.createElement('div');
  texts.className = 'history-texts';
  const original = document.createElement('p');
  original.className = 'history-original';
  original.lang = item.sourceLanguage === 'und' ? '' : item.sourceLanguage;
  appendHighlighted(original, item.originalText, term);
  const translated = document.createElement('p');
  translated.className = 'history-translated';
  translated.lang = item.targetLanguage;
  appendHighlighted(translated, item.translatedText, term);
  texts.append(original, translated);

  const actions = document.createElement('div');
  actions.className = 'history-actions';
  const copy = document.createElement('button');
  copy.type = 'button';
  copy.className = 'icon-btn';
  copy.title = 'Copy translation';
  copy.setAttribute('aria-label', 'Copy translation');
  copy.insertAdjacentHTML('beforeend', ICONS.copy);
  copy.addEventListener('click', () => copyText(item.translatedText));
  const remove = document.createElement('button');
  remove.type = 'button';
  remove.className = 'icon-btn icon-btn-danger';
  remove.title = 'Delete this translation';
  remove.setAttribute('aria-label', 'Delete this translation');
  remove.insertAdjacentHTML('beforeend', ICONS.trash);
  remove.addEventListener('click', () => deleteItem(item.id, li, remove));
  actions.append(copy, remove);

  li.append(meta, texts, actions);
  return li;
}

function updateSummary(filters) {
  const shown = state.loaded;
  if (state.totalItems === 0) {
    els.count.textContent = '';
    els.empty.hidden = false;
    if (hasFilters(filters)) {
      els.emptyTitle.textContent = 'No matching translations';
      els.emptyText.textContent = 'Try a different search term or clear the filters.';
      els.emptyAction.hidden = true;
    } else {
      els.emptyTitle.textContent = 'No translations yet';
      els.emptyText.textContent = 'Translate some text or speak a phrase, and it will appear here.';
      els.emptyAction.hidden = false;
    }
  } else {
    els.empty.hidden = true;
    els.count.textContent = `Showing ${formatCount(shown)} of ${formatCount(state.totalItems)} translation${state.totalItems === 1 ? '' : 's'}`;
  }
  els.loadMore.hidden = state.page + 1 >= state.totalPages;
  // Any result means there is history to clear; an empty result only proves "none" when unfiltered.
  if (state.totalItems > 0) state.anyHistory = true;
  else if (!hasFilters(filters)) state.anyHistory = false;
  els.clearAll.disabled = !state.anyHistory;
}

/** Loads the first page for the current filters (replace = true) or appends the next page. */
async function load({ replace }) {
  const filters = currentFilters();
  const page = replace ? 0 : state.page + 1;
  const request = ++state.request;
  els.list.setAttribute('aria-busy', 'true');
  hideAlert(els.alertHost);
  try {
    const result = await api.history({ ...filters, page, size: PAGE_SIZE });
    if (request !== state.request) return; // a newer search started meanwhile
    if (replace) {
      els.list.replaceChildren();
      state.loaded = 0;
    }
    result.items.forEach((item) => els.list.append(renderItem(item, filters.search)));
    Object.assign(state, { page: result.page, totalItems: result.totalItems, totalPages: result.totalPages });
    state.loaded += result.items.length;
    updateSummary(filters);
  } catch (error) {
    if (request === state.request && error.status !== 401) showAlert(els.alertHost, error.message);
  } finally {
    if (request === state.request) els.list.setAttribute('aria-busy', 'false');
  }
}

async function deleteItem(id, li, button) {
  button.disabled = true;
  try {
    await api.deleteTranslation(id);
    li.classList.add('is-removing');
    setTimeout(() => {
      li.remove();
      state.totalItems -= 1;
      state.loaded -= 1;
      if (!hasFilters(currentFilters())) state.anyHistory = state.totalItems > 0;
      updateSummary(currentFilters());
    }, 250);
    showToast('Translation deleted');
  } catch (error) {
    button.disabled = false;
    showToast(error.status === 404 ? 'That translation was already deleted.' : error.message, 'error');
    if (error.status === 404) li.remove();
  }
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    showToast('Translation copied to clipboard');
  } catch {
    showToast('Could not copy. Select the text and copy it manually.', 'error');
  }
}

function confirmClear() {
  els.dialogCount.textContent = hasFilters(currentFilters()) ? 'all' : `all ${formatCount(state.totalItems)}`;
  els.dialog.returnValue = '';
  els.dialog.showModal();
}

async function onDialogClose() {
  if (els.dialog.returnValue !== 'confirm') return;
  await withBusyButton(els.clearAll, 'Clearing…', async () => {
    try {
      const { deleted } = await api.clearHistory();
      state.anyHistory = false;
      showToast(`Deleted ${formatCount(deleted)} translation${deleted === 1 ? '' : 's'}`);
      await load({ replace: true });
    } catch (error) {
      showAlert(els.alertHost, error.message);
    }
  });
  els.clearAll.disabled = !state.anyHistory;
}

function init() {
  fillLanguageOptions(els.language);
  // Prefill from the URL so filtered views can be linked to, e.g. history.html?language=zu
  const params = new URLSearchParams(window.location.search);
  if (params.get('search')) els.search.value = params.get('search');
  if (params.get('language')) els.language.value = params.get('language');

  const reload = () => load({ replace: true });
  els.search.addEventListener('input', debounce(reload, 300));
  els.language.addEventListener('change', reload);
  els.filters.addEventListener('change', (event) => { if (event.target.name === 'type') reload(); });
  els.filters.addEventListener('submit', (event) => { event.preventDefault(); reload(); });
  els.loadMore.addEventListener('click', () => withBusyButton(els.loadMore, 'Loading…', () => load({ replace: false })));
  els.clearAll.addEventListener('click', confirmClear);
  els.dialog.addEventListener('close', onDialogClose);
  reload();
}

if (requireAuth()) {
  renderAppShell();
  init();
}
