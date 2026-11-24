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

## Phase 4 — Voice translation ✅ (live provider pending)
- ✅ Speech language support checked against provider docs:
  - Azure Speech supports `af-ZA` and `zu-ZA` but **not isiXhosa**.
  - Google v1 supports `af-ZA` and `zu-ZA`, and `xh-ZA` only on the `latest_long`/`latest_short` models.
- ✅ `SpeechToTextProvider` with **Azure AI Speech** (REST for short audio) and **Google Speech-to-Text v1** adapters (`STT_PROVIDER`); no provider → 503 and a startup warning
- ✅ Upload safety (`WavAudio`): the actual bytes are checked:
  - RIFF/WAVE, PCM, 16 kHz, mono, 16-bit, 0.3–60 s;
  - 10 MB multipart cap (413);
  - audio held in memory only, never stored.
- ✅ `POST /api/translations/voice`: validates languages against the speech provider's support, then recognise → translate → save as `VOICE`; 422 when no speech is heard
- ✅ `GET /api/languages` now reports `voiceInput` per language, plus `voiceInputAvailable` and `voiceInputProvider`
- ✅ Browser recorder: MediaRecorder captures in the browser's native format, the Web Audio API resamples it to 16 kHz mono, and it's encoded as WAV. This works the same in Chrome, Edge, Firefox and Safari.
- ✅ Recording UI:
  - mic button with a mode per language (server / browser fallback / choose a language / unavailable);
  - permission request, a live timer with a 60 s auto-stop, a level meter, Stop & translate, Cancel and Esc;
  - processing state; the recognised text fills the input; the microphone is released on stop, cancel or leaving the page.
- ✅ Web Speech API fallback when the server can't recognise the language (e.g. isiXhosa with Azure), labelled as browser recognition with a privacy note
- ✅ Friendly errors: microphone blocked, no microphone, microphone busy, unsupported browser, recording too short, no speech, provider failure
- ✅ Backend tests: **98/98 passing**
- ✅ Verified in the browser against real MySQL, using the real Azure translation and speech adapters over HTTP against a **test-only** stand-in. The microphone was replaced by a generated tone, and the button presses were real mouse clicks. Checked:
  - the WAV on the wire: 16 kHz / mono / 16-bit, with the exact Azure Content-Type;
  - the full flow to a `VOICE` record;
  - the mode per language (isiXhosa → browser fallback);
  - silence → 422 message; blocked mic; missing mic; Cancel (nothing uploaded);
  - phone layout.
- ✅ Bugs found and fixed:
  1. Spring rejected Azure's documented `audio/wav; codecs=audio/pcm; samplerate=16000` Content-Type, so every Azure voice request would have failed. The header is now sent verbatim and the bytes are streamed.
  2. The level meter's `AudioContext` was created after the permission prompt, so autoplay rules left it suspended (dead meter). It's now created during the click.
  3. On phones, the recording buttons overflowed the panel.
- ⬜ **Live speech recognition with a real provider**: needs `STT_PROVIDER` and a key, plus a real microphone test in your browser
- ⬜ Frontend automated tests for the recorder and WAV encoder (Phase 7; the encoder was checked byte by byte with Node this phase)

## Phase 5 — Text-to-speech ✅ (live provider pending)
- ✅ `TextToSpeechProvider` with **Azure AI Speech** (SSML → MP3) and **Google Cloud Text-to-Speech** (MP3) adapters (`TTS_PROVIDER`)
- ✅ Voices come from the provider's **live voice list** (cached for 24 h):
  - grouped by language, with the natural default first (en-US, pt-BR, …);
  - Mandarin means mainland Simplified only;
  - Google voice types that don't support speed changes are left out.
- ✅ SSML safety: the text is XML-escaped (including illegal control characters), and voice ids must come from the provider's own list, so a request can't inject SSML
- ✅ `GET /api/speech/voices`, `POST /api/speech/synthesize`:
  - audio is streamed and **never stored**;
  - provider input limits are enforced (Google: 5000 bytes);
  - the rate limit is shared with the other AI endpoints.
- ✅ `GET /api/languages` reports `voiceOutput` per language, and keeps working (with no server voices advertised) if the voice list is temporarily unavailable
- ✅ Player:
  - one button for Listen / Pause / Resume, with a loading spinner (pressing it while loading cancels), plus Stop;
  - voice picker and speed (0.5×–2×);
  - speed defaults to the account's `speech_speed`; voice and speed are remembered per device;
  - recent clips are cached in memory, so replay is instant and makes no new request;
  - a clear message if the browser blocks autoplay.
- ✅ Browser speech synthesis as the fallback when the server has no voice for a language. If neither has a voice, Listen is disabled and says why.
- ✅ Backend tests: **112/112 passing**
- ✅ Verified in the browser using the real Azure TTS adapter over HTTP against a **test-only** stand-in, with real mouse clicks. Checked:
  - the request on the wire: chosen voice, `rate='+25%'`, MP3 output format, User-Agent;
  - real audio playing, pausing, resuming, stopping and ending on its own;
  - replay served from the cache (one synthesis request);
  - the per-language fallbacks: isiZulu and Afrikaans on the server, French on the browser, isiXhosa disabled with an explanation;
  - phone width.
- ✅ Bug found and fixed: some browser voices fire `start` late. If the user paused before that, the late event flipped the UI back to "playing" and the next press paused again instead of resuming. Browser playback now goes through a `loading` state that only `start` can end, and a pause that doesn't take effect is detected and stopped cleanly.
- ⬜ **Live synthesis with a real provider**: needs `TTS_PROVIDER` and a key; then listen to one translation
- ⬜ Automated frontend tests for the player (Phase 7)

## Phase 6 — Dashboard, history and preferences ✅
- ✅ History API:
  - newest-first paging (max 50 per page);
  - case-insensitive search of the original and translated text, with `%` and `_` matched literally;
  - filters by language (source or target) and type;
  - built with the type-safe Criteria API, so no SQL is built from strings.
- ✅ Authorisation: a user can't read or delete another user's record. They get **404**, not 403, so ids can't be probed. Clear-all only touches the caller's rows. Covered by tests.
- ✅ Dashboard API:
  - totals, voice/text split, rolling 7 days;
  - top 5 languages with share (the undetermined `und` is excluded);
  - zero-filled daily activity bucketed in the **caller's time zone**;
  - recent activity.
- ✅ Preferences API: default source (`auto` allowed) and target languages plus speaking speed, validated against the catalog
- ✅ History page:
  - debounced search with highlighted matches, language filter, All/Text/Voice toggle, "Load more";
  - per-item copy and delete;
  - "Clear history" behind a confirmation dialog;
  - distinct empty states for "no history" and "no matches";
  - linkable filters (`history.html?language=zu`).
- ✅ Dashboard page:
  - KPI tiles;
  - a 14-day column chart (single series in the validated accent colour, 24px bars with 4px rounded tops, hairline grid, hover **and** keyboard tooltips, a table view);
  - ranked language bars;
  - recent activity;
  - redraws on resize.
- ✅ Profile page: a preferences form. Saving clears this device's remembered languages and speed, so the account defaults apply.
- ✅ Navigation: Translator · Dashboard · History · Profile
- ✅ Backend tests: **132/132 passing**
- ✅ Verified in the browser against real MySQL. Checked:
  - dashboard numbers matching the API;
  - hover tooltip;
  - history search, filters, empty states, delete, and clear (both cancel and confirm, with real clicks);
  - preferences flowing through to the translator;
  - phone layouts.
- ✅ Bugs found and fixed:
  1. **Timestamps were stored 2 hours early.** `hibernate.jdbc.time_zone=UTC` converted `LocalDateTime` through the JVM's zone. It was invisible to the app, but every value in MySQL was wrong. The setting was removed, and a regression test is proven to fail with it and pass without it. Rows written before this fix (test accounts only) are 2 hours early.
  2. The language bars had different track lengths, so they couldn't be compared. The figure column now has a fixed width.
  3. On phones, the stat tiles filled the first screen. They're now a 2×2 grid.

## Phase 7 — QA ⬜
Frontend tests · security tests (cross-user access, rate limiting) · bug fixing

## Phase 8 — Deployment and docs ⬜
Dockerfile · docker-compose (MySQL + backend + Nginx) · README completion · API docs · screenshots
