import { api } from '../api/client.js';

const statusEl = document.querySelector('[data-api-status]');
const statusText = statusEl?.querySelector('[data-api-status-text]');

function setStatus(state, text) {
  if (!statusEl) return;
  statusEl.dataset.state = state; // 'checking' | 'up' | 'degraded' | 'down'
  statusText.textContent = text;
}

async function checkApi() {
  setStatus('checking', 'Checking API…');
  try {
    const health = await api.health();
    setStatus('up', `API online · v${health.version}`);
  } catch (error) {
    if (error.status === 503) {
      setStatus('degraded', 'API online · database unavailable');
    } else {
      setStatus('down', 'API offline');
      console.warn('[LinguaVoice] Health check failed:', error.message);
    }
  }
}

function initMobileNav() {
  const toggle = document.querySelector('[data-nav-toggle]');
  const nav = document.getElementById('site-nav');
  if (!toggle || !nav) return;
  toggle.addEventListener('click', () => {
    const open = toggle.getAttribute('aria-expanded') === 'true';
    toggle.setAttribute('aria-expanded', String(!open));
    nav.classList.toggle('is-open', !open);
  });
  nav.querySelectorAll('a').forEach((link) => link.addEventListener('click', () => {
    toggle.setAttribute('aria-expanded', 'false');
    nav.classList.remove('is-open');
  }));
}

document.querySelectorAll('[data-year]').forEach((el) => { el.textContent = String(new Date().getFullYear()); });
initMobileNav();
checkApi();
