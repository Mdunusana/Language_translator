// Small, locale-aware formatting helpers shared by the dashboard and history pages.

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const number = new Intl.NumberFormat();
const compact = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 });

/** "2 minutes ago", "yesterday", or a full date for anything older than a week. */
export function timeAgo(isoString) {
  const date = new Date(isoString);
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) return 'just now';
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), 'hour');
  if (abs < 7 * 86400) return relative.format(Math.round(seconds / 86400), 'day');
  return dateTime.format(date);
}

export function fullDateTime(isoString) {
  return dateTime.format(new Date(isoString));
}

/** 1,284 below 10,000; 12.9K above (stat-tile convention). */
export function formatCount(value) {
  return value < 10000 ? number.format(value) : compact.format(value);
}

export function browserTimeZone() {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch { return 'UTC'; }
}

/** Wraps an async function so it runs at most once per `ms` of quiet (for search-as-you-type). */
export function debounce(fn, ms) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}
