# LinguaVoice — AI Language & Voice Translator

LinguaVoice is a full-stack web application for talking across languages. You speak or type, the AI translates, and you can listen to the result.

> **Status:** Phase 4 (voice translation) is complete. Real translations and speech recognition need provider keys (see step 2). See [docs/CHECKLIST.md](docs/CHECKLIST.md) for progress.

| Layer | Technology |
|---|---|
| Frontend | HTML5, CSS3, vanilla JavaScript (ES modules), Fetch, MediaRecorder |
| Backend | Java 21, Spring Boot 3.5, Spring Web / Data JPA / Security (JWT resource server), Bean Validation, Maven |
| Database | MySQL 8, Flyway migrations |
| AI | Pluggable Speech-to-Text, Translation and Text-to-Speech providers (backend only) |

The architecture, request flows, security design and risks are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Run locally

### Prerequisites
- JDK 21 and Maven 3.9+
- MySQL 8 server running locally (MySQL Workbench is optional but useful)
- Node.js 18+ (used only to serve the static frontend; no npm packages are needed)

### 1. Create the database (one time)
Open `docs/sql/01-create-database.sql` in MySQL Workbench. Replace the placeholder password with your own and run it as root. This creates `linguavoice_db` (utf8mb4) and a least-privilege `linguavoice` user. Flyway creates the tables when the backend starts.

### 2. Configure secrets
```bash
cp .env.example .env
```
In `.env`, set:
- `DB_PASSWORD`: the password from step 1.
- `JWT_SECRET`: at least 32 random characters, for example from `openssl rand -base64 64`. The backend refuses to start without it.

To enable translation, also set a provider. Without one, the translator page says translation isn't configured and the API returns 503; it never shows invented results.

| Provider | `.env` settings | Where to get the key |
|---|---|---|
| Azure AI Translator | `TRANSLATION_PROVIDER=azure`, `TRANSLATION_API_KEY`, `TRANSLATION_REGION` | Azure portal → create a **Translator** resource (free F0 tier) → *Keys and Endpoint* → KEY 1 and *Location/Region* |
| Google Cloud Translation | `TRANSLATION_PROVIDER=google`, `TRANSLATION_API_KEY` | Google Cloud console → enable **Cloud Translation API** → *Credentials* → create an API key (restrict it to that API) |

To enable **server-side voice input** (speech-to-text), set a speech provider too:

| Provider | `.env` settings | Where to get the key | isiXhosa voice input |
|---|---|---|---|
| Azure AI Speech | `STT_PROVIDER=azure`, `STT_API_KEY`, `STT_REGION` | Azure portal → create a **Speech** resource → *Keys and Endpoint* | ✗ not supported by Azure |
| Google Cloud Speech-to-Text | `STT_PROVIDER=google`, `STT_API_KEY` | Google Cloud console → enable **Cloud Speech-to-Text API** → API key | ✓ (`latest_long` model) |

Without a speech provider, the microphone uses the browser's own speech recognition where the browser supports it. In Chrome and Edge that sends audio to the browser vendor, and the button's tooltip says so.

Restart the backend after changing `.env`. The startup log shows which providers are active (`Text translation provider: …`, `Speech-to-text provider: …`), or a warning for each disabled one. The `.env` file is git-ignored.

### 3. Start the backend (port 8081)
```bash
cd backend && mvn spring-boot:run
```
Check it with `curl http://localhost:8081/api/health`. The expected response is `{"status":"UP", ..., "database":"UP"}`.

### 4. Start the frontend (port 5500)
```bash
node frontend/dev-server.mjs
```
Open http://localhost:5500. The dev server serves the pages and forwards (proxies) `/api/*` to the backend, so the browser only ever talks to one origin, as it will behind Nginx in production. It also sends a strict Content-Security-Policy.

Options: `--port 5500` sets the frontend port, and `--api http://localhost:8081` sets the backend address.

## Tests
```bash
cd backend && mvn test
```
98 tests use an in-memory H2 database in MySQL mode and run the real Flyway migrations. They need no MySQL server and make no AI provider calls: providers are replaced by Mockito mocks, and the Azure and Google adapters are tested against a mock HTTP server (`MockRestServiceServer`).

| Suite | Covers |
|---|---|
| `AuthControllerTest` | registration, BCrypt hashing, duplicate and case-insensitive emails, validation, login, identical errors for wrong password and unknown email, tampered/expired/foreign-issuer tokens, tokens of deleted accounts |
| `UserControllerTest` | profile read/update, email conflicts, password change, users only ever seeing their own data |
| `AuthServiceTest` | service logic with Mockito mocks, including the timing-attack defence and the registration race |
| `ConcurrentUserUpdateTest` | regression: overlapping profile and password updates must not overwrite each other |
| `TranslationControllerTest` | translate and save, auto-detect, server-side validation, unsupported languages, 429 rate limit, 503 when not configured, language list |
| `TranslationServiceTest` | validation order, history saving, `und` when the language isn't detected, nothing saved when the provider fails |
| `AzureTranslationProviderTest`, `GoogleTranslationProviderTest` | exact HTTP contract (URL, headers, body), code mapping (`zh` ↔ `zh-Hans`/`zh-CN`), caching, mapping of provider errors |
| `RateLimiterTest`, `TranslationProviderConfigTest` | token bucket per user, provider selection from configuration |
| `VoiceTranslationControllerTest` | upload → recognition → translation → `VOICE` record (audio not stored), unsupported voice language, rejected audio, no speech (422), not configured (503), provider failure, missing parts, per-language `voiceInput` flags |
| `WavAudioTest` | upload safety: bytes must be RIFF/WAVE, 16 kHz mono 16-bit PCM, 0.3–60 s; the file name and declared type are ignored |
| `AzureSpeechToTextProviderTest`, `GoogleSpeechToTextProviderTest`, `SpeechProviderConfigTest` | exact speech API contracts, no-speech handling, isiXhosa only on Google's `latest_long` model, provider selection |
| `HealthControllerTest`, `SchemaMigrationTest` | health check, 401 handling, CORS, database constraints |

## Database
Three tables: `users`, `translations` and `user_preferences`. Both child tables reference `users` with `ON DELETE CASCADE`. Email is unique, and there are check constraints on `translation_type` and `speech_speed`. Indexes cover history queries per user. See `backend/src/main/resources/db/migration/`.

To inspect the data in MySQL Workbench, connect as `linguavoice@localhost` and open `linguavoice_db`. The `password` column holds BCrypt hashes (`$2a$12$…`), never plain text.

## API (implemented so far)
All responses are JSON. Errors share one shape: `{ timestamp, status, error, message, path, fieldErrors? }`.

| Method | Path | Auth | Description | Success |
|---|---|---|---|---|
| GET | `/api/health` | public | API and database status | 200 |
| POST | `/api/auth/register` | public | `{fullName, email, password, confirmPassword}`. Creates the account and returns a token | 201 |
| POST | `/api/auth/login` | public | `{email, password}`. Returns `{accessToken, tokenType, expiresAt, user}` | 200 |
| GET | `/api/users/profile` | Bearer | The signed-in user's profile and preferences | 200 |
| PUT | `/api/users/profile` | Bearer | `{fullName, email}` | 200 |
| PUT | `/api/users/password` | Bearer | `{currentPassword, newPassword, confirmNewPassword}` | 204 |
| GET | `/api/languages` | Bearer | `{translationAvailable, provider, languages: [{code, name, nativeName, textTranslation, voiceInput, voiceOutput}], message?}`. Lists only languages the configured provider supports | 200 |
| POST | `/api/translations/text` | Bearer | `{text (≤ 5000 chars), sourceLanguage ("auto" or a code), targetLanguage}`. Translates, saves to history, and returns the record including `sourceDetected` | 201 |
| POST | `/api/translations/voice` | Bearer | multipart: `audio` (16 kHz mono 16-bit PCM WAV, ≤ 60 s), `sourceLanguage` (the spoken language), `targetLanguage`. Recognises, translates and saves a `VOICE` record; `originalText` is the recognised speech. **The audio is never stored.** | 201 |

Error statuses:
- **400** – validation failed (`fieldErrors` says which field and why).
- **401** – missing, invalid or expired token, or wrong credentials.
- **409** – the email address is already in use.
- **413** – the upload is larger than 10 MB.
- **422** – no speech could be recognised in the recording.
- **429** – too many AI requests; wait for the number of seconds in the `Retry-After` header.
- **502** – the AI provider failed or timed out.
- **503** – no AI provider is configured, or its credentials were rejected.

Supported languages: English, Afrikaans, isiZulu, isiXhosa, Spanish, French, Portuguese, German, Mandarin Chinese, Arabic, Japanese, Swahili. A language is offered only when the configured provider confirms it supports it.

Passwords must be 8–72 characters and contain a letter and a number.

---
*Remaining README sections (features, environment variable reference, screenshots, Docker) are completed in Phase 8.*
