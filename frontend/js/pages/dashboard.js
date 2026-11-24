import { api } from '../api/client.js';
import { requireAuth, session } from '../auth/session.js';
import { renderAppShell } from '../components/app-shell.js';
import { renderBarList, renderColumnChart } from '../components/charts.js';
import { browserTimeZone, formatCount, timeAgo } from '../components/format.js';
import { showAlert } from '../components/forms.js';
import { languageName } from '../components/languages.js';

const els = {
  greeting: document.querySelector('[data-greeting]'),
  host: document.querySelector('.container'),
  kpis: document.querySelector('[data-kpis]'),
  grid: document.querySelector('.dashboard-grid'),
  chart: document.querySelector('[data-activity-chart]'),
  table: document.querySelector('[data-activity-table]'),
  languages: document.querySelector('[data-languages]'),
  recent: document.querySelector('[data-recent]'),
  empty: document.querySelector('[data-empty]'),
};

const dayLabel = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
const dayShort = new Intl.DateTimeFormat(undefined, { day: 'numeric' });
let activity = [];

function setStat(key, value, sub = '') {
  document.querySelector(`[data-stat="${key}"]`).textContent = formatCount(value);
  document.querySelector(`[data-stat-sub="${key}"]`).textContent = sub || ' ';
}

function percent(part, whole) {
  return whole === 0 ? 0 : Math.round((part / whole) * 100);
}

/** API dates are plain YYYY-MM-DD: build them in local time so the label shows the same calendar day. */
function parseDay(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function renderActivity() {
  const data = activity.map((day) => {
    const date = parseDay(day.date);
    return { label: dayLabel.format(date), shortLabel: dayShort.format(date), value: day.count };
  });
  renderColumnChart(els.chart, data, {
    valueLabel: (v) => `${formatCount(v)} translation${v === 1 ? '' : 's'}`,
    ariaLabel: 'Translations per day for the last 14 days. Use Tab to move between days.',
  });
  els.table.replaceChildren(...data.map((d) => {
    const row = document.createElement('tr');
    row.append(Object.assign(document.createElement('td'), { textContent: d.label }),
      Object.assign(document.createElement('td'), { textContent: formatCount(d.value) }));
    return row;
  }));
}

function renderLanguages(top) {
  if (!top.length) {
    els.languages.replaceChildren(Object.assign(document.createElement('p'), { className: 'muted-note', textContent: 'No languages yet.' }));
    return;
  }
  renderBarList(els.languages, top.map((language) => ({
    name: language.name,
    value: language.count,
    detail: `${formatCount(language.count)} · ${language.share}%`,
  })));
}

function renderRecent(items) {
  els.recent.replaceChildren(...items.map((item) => {
    const li = document.createElement('li');
    const text = Object.assign(document.createElement('span'), { className: 'recent-text', textContent: item.translatedText });
    text.lang = item.targetLanguage;
    const meta = Object.assign(document.createElement('span'), {
      className: 'recent-meta',
      textContent: `${languageName(item.sourceLanguage)} → ${languageName(item.targetLanguage)} · ${item.translationType === 'VOICE' ? 'Voice' : 'Text'} · “${item.originalText}”`,
    });
    const time = Object.assign(document.createElement('time'), { dateTime: item.createdAt, textContent: timeAgo(item.createdAt) });
    li.append(text, meta, time);
    return li;
  }));
}

async function init() {
  const firstName = session.user()?.fullName?.split(' ')[0];
  if (firstName) els.greeting.textContent = `Welcome back, ${firstName}`;

  let stats;
  let recent;
  try {
    [stats, recent] = await Promise.all([api.statistics(browserTimeZone()), api.recentActivity(5)]);
  } catch (error) {
    if (error.status !== 401) showAlert(els.host, `Could not load your statistics. ${error.message}`);
    return;
  }

  setStat('total', stats.totalTranslations);
  setStat('voice', stats.voiceTranslations, stats.totalTranslations ? `${percent(stats.voiceTranslations, stats.totalTranslations)}% of all` : '');
  setStat('text', stats.textTranslations, stats.totalTranslations ? `${percent(stats.textTranslations, stats.totalTranslations)}% of all` : '');
  setStat('week', stats.lastSevenDays, 'Rolling 7 days');

  if (stats.totalTranslations === 0) {
    els.grid.hidden = true;
    els.empty.hidden = false;
    return;
  }
  activity = stats.dailyActivity;
  renderActivity();
  renderLanguages(stats.topLanguages);
  renderRecent(recent);

  // Re-draw the chart at the new width when the layout changes (rotation, window resize).
  let lastWidth = els.chart.clientWidth;
  new ResizeObserver(() => {
    if (Math.abs(els.chart.clientWidth - lastWidth) > 4) {
      lastWidth = els.chart.clientWidth;
      renderActivity();
    }
  }).observe(els.chart);
}

if (requireAuth()) {
  renderAppShell();
  init();
}
