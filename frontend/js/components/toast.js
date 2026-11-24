// Small, accessible, auto-dismissing notifications ("Copied to clipboard").

let region = null;

function ensureRegion() {
  if (region) return region;
  region = document.createElement('div');
  region.className = 'toast-region';
  region.setAttribute('role', 'status');
  region.setAttribute('aria-live', 'polite');
  document.body.append(region);
  return region;
}

/** @param {'success'|'error'|'info'} type */
export function showToast(message, type = 'success', durationMs = 2600) {
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.dataset.type = type;
  toast.textContent = message;
  ensureRegion().append(toast);
  requestAnimationFrame(() => toast.classList.add('is-visible'));
  setTimeout(() => {
    toast.classList.remove('is-visible');
    setTimeout(() => toast.remove(), 300);
  }, durationMs);
}
