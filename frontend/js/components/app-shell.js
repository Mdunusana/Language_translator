// Renders the signed-in top bar (logo, navigation, user menu) into <header data-app-shell>.
// Navigation lists only pages that exist; each phase adds its page here.

import { session } from '../auth/session.js';

const ICONS = {
  translator: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 8 6 6"/><path d="m4 14 6-6 2-3"/><path d="M2 5h12"/><path d="M7 2h1"/><path d="m22 22-5-10-5 10"/><path d="M14 18h6"/></svg>',
  dashboard: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3v18h18"/><path d="M7 16v-5"/><path d="M12 16V8"/><path d="M17 16v-3"/></svg>',
  history: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l3 3"/></svg>',
  profile: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
  signOut: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/></svg>',
  logo: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z"/><path d="M19 11a7 7 0 0 1-14 0"/><path d="M12 18v3"/></svg>',
};

/** Pages reachable from the top bar. */
const NAV_ITEMS = [
  { href: 'translator.html', label: 'Translator', icon: ICONS.translator },
  { href: 'dashboard.html', label: 'Dashboard', icon: ICONS.dashboard },
  { href: 'history.html', label: 'History', icon: ICONS.history },
  { href: 'profile.html', label: 'Profile & settings', icon: ICONS.profile },
];

function initials(fullName = '') {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] ?? '?').concat(parts.length > 1 ? parts.at(-1)[0] : '').toUpperCase();
}

export function renderAppShell() {
  const header = document.querySelector('[data-app-shell]');
  if (!header) return;
  const current = window.location.pathname.split('/').pop();

  header.className = 'topbar';
  header.innerHTML = `
    <div class="container">
      <a class="logo" href="translator.html" aria-label="LinguaVoice">
        <span class="logo-mark" aria-hidden="true">${ICONS.logo}</span><span class="logo-text">LinguaVoice</span>
      </a>
      <nav class="app-nav" aria-label="Main">
        ${NAV_ITEMS.map((item) => `
          <a href="${item.href}" title="${item.label}" ${item.href === current ? 'aria-current="page"' : ''}>${item.icon}<span>${item.label}</span></a>
        `).join('')}
      </nav>
      <div class="user-menu">
        <span class="avatar" aria-hidden="true" data-user-initials></span>
        <span class="user-name" data-user-name></span>
        <button class="btn btn-ghost btn-signout" type="button" data-sign-out>${ICONS.signOut}<span class="btn-text">Sign out</span></button>
      </div>
    </div>`;

  updateShellUser(session.user());
  header.querySelector('[data-sign-out]').addEventListener('click', () => session.logout());
}

/** Updates the name/initials shown in the top bar. Uses textContent: names are user input. */
export function updateShellUser(user) {
  const name = user?.fullName ?? '';
  document.querySelectorAll('[data-user-name]').forEach((el) => { el.textContent = name; });
  document.querySelectorAll('[data-user-initials]').forEach((el) => { el.textContent = initials(name); });
}
