# Development checklist

Legend: ✅ done and verified · 🟡 done, not yet verified · ⬜ pending

## Phase 1 — Project setup ✅
- ✅ Development environment checked: JDK 21, Maven 3.9, Node 24, MySQL 8.0 service running, Workbench installed. Docker is **not** installed.
- ✅ Spring Boot 3.5 Maven project: web, validation, JPA, security, Flyway and the MySQL driver
- ✅ Layered package structure, `AppProperties`, `GlobalExceptionHandler`, uniform `ApiError` JSON
- ✅ Security baseline: stateless, deny-by-default, JSON 401, CORS allow-list
- ✅ Flyway `V1__create_core_schema.sql` (users, translations, user_preferences, FKs, indexes, checks)
- ✅ Configuration from environment variables and `.env`; `.env.example` and `.gitignore`
- ✅ `GET /api/health` (public), checking API and database status
- ✅ Frontend structure, design tokens, API client, zero-dependency dev server, responsive landing page
- ✅ Verified against real MySQL 8.0.46: Flyway applied V1, and health reports database UP

## Phase 2 — Database and authentication ✅
- ✅ JPA entities `User`, `UserPreference`, `Translation` (+ `TranslationType`); Hibernate validates them against the Flyway schema on H2 **and** MySQL
- ✅ Repositories, with translation queries always scoped by `userId`
- ✅ `POST /api/auth/register`: validation, email normalisation, BCrypt (cost 12), default preferences, 409 for duplicates (including a race between two registrations)
- ✅ `POST /api/auth/login`: the same 401 message for a wrong password and an unknown email, plus a timing-attack defence
- ✅ JWT (HS256) issued by the API and validated by Spring Security's resource server (signature, expiry, issuer); secret from `JWT_SECRET`
- ✅ `GET/PUT /api/users/profile` and `PUT /api/users/password`; the user always comes from the token, with no `/users/{id}` route
- ✅ A valid token for a deleted account is rejected (401)
- ✅ Bug found in UI testing and fixed: overlapping profile and password updates overwrote each other. Fixed with `@DynamicUpdate`, and a regression test was added.
- ✅ Frontend: `register.html`, `login.html`, `profile.html` (app shell, profile edit, password change)
  - client-side validation that mirrors the backend; server field errors shown inline; password show/hide
  - session in `sessionStorage`, an auth guard on protected pages, a safe `?next=` redirect, automatic sign-out when a token is rejected, sign-out
- ✅ Dev server proxies `/api` (same-origin, like Nginx) and sends a strict Content-Security-Policy; no CSP violations seen
- ✅ Backend tests: **41/41 passing**
- ✅ Verified end to end in the browser against real MySQL: register, duplicate email, validation, profile edit, password change, sign out, log in with the new password (the old one is rejected), handling of a rejected token, and phone width (375 px)
- ⬜ Rate limiting on login (planned with the general rate limiter in Phase 7)
- ⬜ Revoking existing tokens after a password change (tokens currently live until they expire, 60 min by default)
- ⬜ Editing preferences (default languages, speech speed): Phase 6, once the language list exists

## Phase 3 — Text translation ✅ (live provider pending)
- ✅ `TranslationProvider` interface with two real adapters: **Azure AI Translator v3** and **Google Cloud Translation v2**, chosen with `TRANSLATION_PROVIDER`
- ✅ No provider or key: `NotConfiguredTranslationProvider` returns 503 with a clear message and a startup warning. Never fake output.
- ✅ Language codes mapped per provider (`zh` ↔ `zh-Hans` / `zh-CN`); provider language lists cached for 24 h; failed loads are not cached
- ✅ Provider errors mapped to user-safe messages (bad key → 503 "not configured correctly", throttling → 503 busy, failures and timeouts → 502); technical details are only logged
- ✅ `GET /api/languages`: the 12 catalog languages, filtered by what the provider really supports
- ✅ `POST /api/translations/text`: validation (≤ 5000 chars, language allow-list, source ≠ target), auto-detect, saving to history (`TEXT`), `und` when the language isn't detected
- ✅ The provider call runs outside the database transaction, so no DB connection is held during slow HTTP calls
- ✅ Per-user token-bucket rate limit for AI endpoints (`AI_RATE_LIMIT_PER_MINUTE`, 429 + `Retry-After`)
- ✅ Translator page:
  - language selectors with auto-detect, swap (uses the detected language and moves the result into the input)
  - character counter, clear, Ctrl+Enter, loading shimmer, "Detected: …" chip, copy to clipboard
  - listen via the browser voice (fallback; only enabled when the browser has a voice for the language)
  - remembers the last languages; "not configured" banner; provider credit
- ✅ Backend tests: **74/74 passing**
- ✅ Verified in the browser against real MySQL, using the real Azure adapter over HTTP against a **test-only** stand-in (in the scratchpad, not in the repo). Checked:
  - correct headers, query parameters and code mapping on the wire
  - auto-detect, swap, Mandarin, the client-side guards
  - a provider outage → clear 502 message
  - the "not configured" state
  - phone width
- ✅ UI bugs found and fixed: the `hidden` attribute was overridden by component `display` styles (fixed globally); stray whitespace in the output placeholder; nav labels cut off on phones (now icon-only)
- ⬜ **A live call to a real provider**: needs your Azure or Google key in `.env`. Then translate one phrase and confirm the result.
- ⬜ Text-to-speech through the backend (Phase 5); the browser voice is the fallback until then

## Phase 4 — Voice translation ⬜
MediaRecorder UI · upload validation · `SpeechToTextProvider` · `/api/translations/voice` · Web Speech fallback · tests

## Phase 5 — Text-to-speech ⬜
`TextToSpeechProvider` · `/api/speech/synthesize` · player (play/pause/stop, voice, speed) · fallback

## Phase 6 — Dashboard and history ⬜
Statistics and recent-activity endpoints · charts · history search, filter and delete · preferences editing

## Phase 7 — QA ⬜
Frontend tests · security tests (cross-user access, rate limiting) · bug fixing

## Phase 8 — Deployment and docs ⬜
Dockerfile · docker-compose (MySQL + backend + Nginx) · README completion · API docs · screenshots
