// Minimal browser environment for unit tests under Node's built-in test runner (no jsdom, no dependencies).
// Import this FIRST in every test file. Each test file runs in its own process, so stubs never leak.

class MemoryStorage {
  #items = new Map();
  getItem(key) { return this.#items.has(key) ? this.#items.get(key) : null; }
  setItem(key, value) { this.#items.set(key, String(value)); }
  removeItem(key) { this.#items.delete(key); }
  clear() { this.#items.clear(); }
}

export const fakeLocation = {
  pathname: '/translator.html',
  search: '',
  hostname: 'localhost',
  assigned: [],
  replaced: [],
  assign(url) { this.assigned.push(url); },
  replace(url) { this.replaced.push(url); },
  reset(pathname = '/translator.html', search = '') {
    Object.assign(this, { pathname, search, assigned: [], replaced: [] });
  },
};

globalThis.window = globalThis;
globalThis.sessionStorage = new MemoryStorage();
globalThis.localStorage = new MemoryStorage();
Object.defineProperty(globalThis, 'location', { value: fakeLocation, configurable: true, writable: true });

/** Replaces navigator (Node defines a read-only one) for the current test file. */
export function setNavigator(value) {
  Object.defineProperty(globalThis, 'navigator', { value, configurable: true, writable: true });
}

export function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** Stores a token the way the app does: { token, expiresAt }. */
export function storeToken(token = 'test-token', expiresInMs = 60_000) {
  sessionStorage.setItem('linguavoice.token', JSON.stringify({ token, expiresAt: new Date(Date.now() + expiresInMs).toISOString() }));
}

export const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
