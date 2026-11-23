// Frontend runtime configuration.
// This file must NEVER contain API keys or secrets — all AI provider calls go through the Java backend.

export const config = Object.freeze({
  // Always same-origin: the dev server (frontend/dev-server.mjs) and Nginx in production both proxy
  // /api to the Spring Boot backend, so the backend's address is configured in one place only.
  apiBaseUrl: '/api',
  requestTimeoutMs: 15000,
});
