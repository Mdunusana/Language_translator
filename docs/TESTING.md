# LinguaVoice — Testing

LinguaVoice is tested at three levels:
1. **Backend tests** (JUnit 5, Mockito, MockMvc): 166 tests.
2. **Frontend unit tests** (Node's built-in test runner): 50 tests.
3. **Manual end-to-end checks** in a real browser against MySQL, done at the end of every phase. They're listed in [CHECKLIST.md](CHECKLIST.md).

No automated test calls a paid AI service. The AI providers are replaced by Mockito mocks, and the provider adapters are tested against a mock HTTP server.

## Running the tests

### Backend
```bash
cd backend && mvn test
```
- Requirements: JDK 21 and Maven. No MySQL, Docker or API keys needed.
- Tests use an in-memory **H2** database in MySQL compatibility mode, running the **real Flyway migrations** (V1, V2). Hibernate's `ddl-auto=validate` also checks the entities against that schema.
- Reports: `backend/target/surefire-reports/`.

### Frontend
```bash
cd frontend && npm test
```
This runs `node --test "tests/*.test.mjs"`.
- Requirements: Node 20 or newer. There are **no npm packages to install**: `package.json` exists only for the script name.
- Browser-only APIs (storage, `fetch`, `location`, microphone, `AudioContext`, `MediaRecorder`, `<audio>`, `speechSynthesis`) are replaced by small stand-ins in `tests/setup.mjs` and at the top of each test file. Each test file runs in its own process, so stand-ins never leak between files.

## What is covered

### Backend
| Area | Test classes | Highlights |
|---|---|---|
| Registration and login | `AuthControllerTest`, `AuthServiceTest` | BCrypt hashing, duplicate emails (letter case ignored, and the race between two registrations), validation, the same 401 message and the same timing for a wrong password and an unknown email |
| Password hashing | `AuthControllerTest.hashesPassword`, `SecurityHardeningTest.neverLeaksPasswordHash` | the stored value is a `$2a$` hash; no response ever contains the hash or the password |
| Brute-force protection | `LoginAttemptLimiterTest`, `LoginLockoutTest` | locked after 5 failures in 15 min (even with the right password); letter case can't dodge the lock; unknown emails behave identically; a success resets; the lock expires |
| Tokens (JWT) | `AuthControllerTest`, `SecurityHardeningTest`, `UserControllerTest` | rejected: tampered, expired, wrong issuer, unsigned (`alg: none`), signed with another key, edited payload, bad subject, deleted account, issued before a password change |
| Authorisation | `HistoryControllerTest`, `UserControllerTest` | another user's translation is **404** for both read and delete; clear-all affects only the caller; the profile always comes from the token |
| Input validation | every controller test, `ApiRobustnessTest` | 19 malformed requests (wrong types, bad JSON, wrong content types, missing parts or parameters, overflowing ids) all give a 4xx JSON error, never a 500 or a stack trace |
| SQL injection | `HistoryControllerTest`, `ApiRobustnessTest` | search uses the Criteria API; `%` and `_` are matched literally; injection-like filter values are rejected |
| Text translation | `TranslationControllerTest`, `TranslationServiceTest` | save to history, auto-detect, unsupported languages, rate limit (429 + `Retry-After`), not configured (503), provider failure (502, nothing saved) |
| Speech translation | `VoiceTranslationControllerTest`, `WavAudioTest` | upload checked by its actual bytes (16 kHz mono 16-bit PCM, 0.3–60 s); recognise → translate → `VOICE` record; the audio is never stored; no speech gives 422 |
| Text-to-speech | `SpeechControllerTest` | MP3 with `no-store`; voice and speed; provider limits; SSML injection escaped |
| Provider adapters | `Azure*ProviderTest`, `Google*ProviderTest` (6 classes) | the exact HTTP contract (URL, headers, body), language-code mapping, caching, mapping of provider errors to user-safe messages |
| History and dashboard | `HistoryControllerTest`, `DashboardControllerTest` | search, filters, paging; statistics; daily buckets in the caller's time zone |
| Database | `SchemaMigrationTest`, `TimestampStorageTest`, `ConcurrentUserUpdateTest` | constraints and cascades; timestamps stored as real UTC on a UTC+2 server; overlapping updates don't overwrite each other |
| Security headers | `SecurityHardeningTest`, `HealthControllerTest` | CSP `default-src 'none'`, `no-referrer`, `nosniff`, `X-Frame-Options: DENY`, `no-store`; the CORS allow-list |

### Frontend
| Area | File | Highlights |
|---|---|---|
| Form validation | `validators.test.mjs` | the same rules as the backend (name, email, password strength and length limit, confirmation) |
| API error handling | `api-client.test.mjs` | `ApiError` with status and field errors; non-JSON errors; server unreachable (status 0); timeouts; a 401 signs out only for authenticated calls, never for a wrong password at login; expired tokens aren't sent; multipart and blob handling |
| Session and redirects | `session.test.mjs` | protected pages redirect to login and remember the page; `?next=` can't redirect to another site (`//evil`, `https:`, `javascript:`, `../`); logout |
| Microphone permissions | `recorder.test.mjs` | each `getUserMedia` error gives a specific message (blocked, no microphone, busy, unknown); a secure context is required; the microphone and audio contexts are always released |
| Audio playback | `speech-player.test.mjs` | the state flow (loading/playing/paused/idle); cached replays; stopping while loading; blocked autoplay; API errors; the browser-voice fallback, including the late-"start" race and voices that can't pause |
| Audio encoding and helpers | `wav.test.mjs`, `helpers.test.mjs` | the WAV header byte by byte, sample clamping, chart axis steps, the catalog mirror, relative time, debounce |

**Mutation check:** the tests are known to catch regressions. Re-introducing the Phase 5 late-"start" bug made exactly the matching player test fail. The timestamp regression test was likewise run against the old setting and failed.

## Not covered by automated tests (and how it was checked instead)
| Gap | Why | How it was verified |
|---|---|---|
| Page scripts (`js/pages/*.js`) that drive the DOM | Testing them would need a DOM library (jsdom) or a browser runner; the project avoids extra dependencies | Manual browser checks every phase, with real mouse clicks for the important flows; see [CHECKLIST.md](CHECKLIST.md) |
| Real AI provider calls | They cost money and need keys | Mock HTTP servers in tests, plus a test-only stand-in over real HTTP during browser checks. **A live check with real keys is still pending.** |
| A real microphone | Not available in automated runs | A generated tone fed through the real recorder → WAV → upload path, with real clicks |
| Multi-instance behaviour of the in-memory rate and lockout limiters | Single-instance design | Documented in [ARCHITECTURE.md](ARCHITECTURE.md); scaling out would move them to Redis |
