// Client-side session: the access token plus a cached copy of the signed-in user for display.
// The backend remains the authority: every protected call is checked server-side.

import { onUnauthorized, tokenStore } from '../api/client.js';

const USER_KEY = 'linguavoice.user';

/** First page after signing in. Becomes translator.html once Phase 3 ships the translator. */
export const HOME_PAGE = 'profile.html';
const LOGIN_PAGE = 'login.html';

/** Only bare page names are accepted as redirect targets, which prevents open redirects via ?next=. */
const SAFE_PAGE = /^[a-z][a-z0-9-]*\.html$/;

export const session = {
  /** @param {{accessToken: string, expiresAt: string, user: object}} auth  AuthResponse from the API */
  start(auth) {
    tokenStore.set(auth.accessToken, auth.expiresAt);
    this.setUser(auth.user);
  },

  isAuthenticated() {
    return tokenStore.get() !== null;
  },

  user() {
    try { return JSON.parse(sessionStorage.getItem(USER_KEY) ?? 'null'); } catch { return null; }
  },

  setUser(user) {
    try { sessionStorage.setItem(USER_KEY, JSON.stringify(user)); } catch { /* storage unavailable */ }
  },

  clear() {
    tokenStore.clear();
    try { sessionStorage.removeItem(USER_KEY); } catch { /* storage unavailable */ }
  },

  logout() {
    this.clear();
    window.location.assign(`${LOGIN_PAGE}?reason=signed-out`);
  },
};

function currentPage() {
  return window.location.pathname.split('/').pop() || 'index.html';
}

function redirectToLogin(reason) {
  const params = new URLSearchParams({ reason, next: currentPage() });
  window.location.replace(`${LOGIN_PAGE}?${params}`);
}

/**
 * Call at the top of every protected page. Returns false (and redirects) when there is no valid session.
 * Also makes any later 401 from the API sign the user out and return them to the login page.
 */
export function requireAuth() {
  onUnauthorized(() => {
    session.clear();
    redirectToLogin('expired');
  });
  if (!session.isAuthenticated()) {
    session.clear();
    redirectToLogin('auth-required');
    return false;
  }
  return true;
}

/** Call on login/register pages: an already signed-in user goes straight to the app. */
export function redirectIfAuthenticated() {
  if (session.isAuthenticated()) {
    window.location.replace(HOME_PAGE);
    return true;
  }
  return false;
}

/** Where to go after signing in: the page the user originally asked for, if it is safe, else HOME_PAGE. */
export function postLoginDestination() {
  const next = new URLSearchParams(window.location.search).get('next');
  return next && SAFE_PAGE.test(next) && next !== LOGIN_PAGE && next !== 'register.html' ? next : HOME_PAGE;
}
