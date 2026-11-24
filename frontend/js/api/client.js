// Single entry point for all HTTP calls to the LinguaVoice backend.
// Every page imports from here so URLs, auth headers, timeouts and error handling stay consistent.

import { config } from '../config.js';

const TOKEN_KEY = 'linguavoice.token';

/** Error thrown for any failed API call. `status` is 0 when the server could not be reached. */
export class ApiError extends Error {
  constructor(message, status, details = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details; // the backend's ApiError JSON body, when present
  }

  /** Per-field validation messages from the backend, e.g. { email: 'Enter a valid email address.' } */
  get fieldErrors() {
    return this.details?.fieldErrors ?? {};
  }
}

/**
 * Access-token storage. sessionStorage is cleared when the tab closes, which limits how long a
 * stolen token could be used. Wrapped in try/catch because storage can be blocked by the browser.
 */
export const tokenStore = {
  get() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(TOKEN_KEY) ?? 'null');
      if (!saved?.token) return null;
      if (saved.expiresAt && Date.parse(saved.expiresAt) <= Date.now()) {
        this.clear();
        return null;
      }
      return saved.token;
    } catch {
      return null;
    }
  },
  set(token, expiresAt) {
    try { sessionStorage.setItem(TOKEN_KEY, JSON.stringify({ token, expiresAt })); } catch { /* storage unavailable */ }
  },
  clear() {
    try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* storage unavailable */ }
  },
};

let unauthorizedHandler = null;

/** Registers what to do when the backend rejects our token (expired, revoked, account deleted). */
export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

/**
 * Performs a request against the backend and returns the parsed JSON body (or null for empty responses).
 * @param {string} path      API path relative to the base URL, e.g. '/health'
 * @param {object} [options]
 * @param {string} [options.method='GET']
 * @param {object|FormData} [options.body]  plain objects are sent as JSON; FormData as multipart
 * @param {boolean} [options.auth=true]     attach the bearer token when one is stored
 * @param {number} [options.timeoutMs]
 * @throws {ApiError}
 */
export async function apiRequest(path, {
  method = 'GET', body, auth = true, timeoutMs = config.requestTimeoutMs, responseType = 'json',
} = {}) {
  // Binary endpoints (audio) also accept JSON so that errors still arrive as the standard ApiError body.
  const headers = { Accept: responseType === 'blob' ? 'audio/mpeg, application/json' : 'application/json' };
  const isMultipart = body instanceof FormData;
  if (body !== undefined && !isMultipart) headers['Content-Type'] = 'application/json';

  const token = auth ? tokenStore.get() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  let response;
  try {
    response = await fetch(`${config.apiBaseUrl}${path}`, {
      method,
      headers,
      body: isMultipart ? body : body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch (error) {
    const message = error.name === 'AbortError'
      ? 'The server took too long to respond. Please try again.'
      : 'Cannot reach the LinguaVoice server. Check that the backend is running.';
    throw new ApiError(message, 0);
  } finally {
    clearTimeout(timer);
  }

  const isJson = (response.headers.get('Content-Type') ?? '').includes('application/json');
  if (response.ok && responseType === 'blob') {
    return response.blob();
  }
  const payload = isJson ? await response.json().catch(() => null) : null;

  if (!response.ok) {
    if (response.status === 401 && token) {
      tokenStore.clear();
      unauthorizedHandler?.();
    }
    throw new ApiError(payload?.message ?? `Request failed with status ${response.status}.`, response.status, payload);
  }
  return payload;
}

/** Typed endpoint helpers. Paths here must match the Spring controllers exactly. */
export const api = {
  health: () => apiRequest('/health', { auth: false }),

  // AuthController — public, so never send a (possibly stale) token
  register: (data) => apiRequest('/auth/register', { method: 'POST', body: data, auth: false }),
  login: (data) => apiRequest('/auth/login', { method: 'POST', body: data, auth: false }),

  // UserController
  getProfile: () => apiRequest('/users/profile'),
  updateProfile: (data) => apiRequest('/users/profile', { method: 'PUT', body: data }),
  changePassword: (data) => apiRequest('/users/password', { method: 'PUT', body: data }),

  // LanguageController / TranslationController
  languages: () => apiRequest('/languages'),
  translateText: (data) => apiRequest('/translations/text', { method: 'POST', body: data, timeoutMs: 25000 }),
  /** @param {FormData} form  audio (WAV), sourceLanguage, targetLanguage */
  translateVoice: (form) => apiRequest('/translations/voice', { method: 'POST', body: form, timeoutMs: 60000 }),

  // HistoryController
  /** @param {{search?: string, language?: string, type?: string, page?: number, size?: number}} filters */
  history: (filters = {}) => {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    }
    return apiRequest(`/translations/history?${params}`);
  },
  deleteTranslation: (id) => apiRequest(`/translations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  clearHistory: () => apiRequest('/translations/history', { method: 'DELETE' }),

  // DashboardController
  statistics: (timezone) => apiRequest(`/dashboard/statistics?timezone=${encodeURIComponent(timezone)}`),
  recentActivity: (limit = 5) => apiRequest(`/dashboard/recent-activity?limit=${limit}`),

  // UserController (preferences)
  updatePreferences: (data) => apiRequest('/users/preferences', { method: 'PUT', body: data }),

  // SpeechController
  voices: (language) => apiRequest(`/speech/voices?language=${encodeURIComponent(language)}`),
  /** @returns {Promise<Blob>} MP3 audio */
  synthesize: (data) => apiRequest('/speech/synthesize', { method: 'POST', body: data, timeoutMs: 45000, responseType: 'blob' }),
};
