# LinguaVoice — Architecture

## 1. Overview

```
 Browser (HTML/CSS/vanilla JS)                     Java 21 · Spring Boot 3.5                       External
┌───────────────────────────┐   HTTPS + JSON    ┌──────────────────────────────────────────┐   ┌─────────────────┐
│ pages/*.js                │  Bearer JWT       │ security/  JWT filter · CORS · rate limit │   │ Speech-to-Text  │
│  ├ MediaRecorder (mic)    │ ───────────────▶  │ controller/  thin REST layer              │   │ provider        │
│  ├ <audio> playback       │  multipart audio  │ service/     business logic               │──▶│ Translation     │
│  └ Web Speech (fallback)  │ ◀───────────────  │ integration/ SpeechToTextProvider ...     │   │ provider        │
│ api/client.js (fetch)     │  JSON / audio     │ repository/  Spring Data JPA              │   │ Text-to-Speech  │
└───────────────────────────┘                   └───────────────┬──────────────────────────┘   │ provider        │
                                                                │ JDBC (Hikari)                └─────────────────┘
                                                        ┌───────▼────────┐
                                                        │ MySQL 8        │  schema owned by Flyway
                                                        │ linguavoice_db │
                                                        └────────────────┘
```

**Rules that hold everywhere**

- The browser never talks to an AI provider and never sees an API key. Every AI call goes through the backend.
- Controllers only map HTTP to service calls. Business rules live in `service/`.
- Entities never leave the service layer. The API returns DTOs (Java records).
- Every query is scoped to the authenticated user's id, so one user cannot read another user's data.
- Flyway owns the schema. Hibernate runs with `ddl-auto=validate` and never changes tables.

## 2. Folder structure

```
linguavoice/
├── backend/                      Spring Boot (Maven)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/linguavoice/
│       │   ├── config/           AppProperties, SecurityConfig, provider wiring
│       │   ├── controller/       REST controllers (thin)
│       │   ├── dto/              request/response records, ApiError
│       │   ├── entity/           JPA entities: User, Translation, UserPreference
│       │   ├── exception/        GlobalExceptionHandler + domain exceptions
│       │   ├── repository/       Spring Data JPA repositories
│       │   ├── security/         JWT service + filter, entry point, UserDetails
│       │   ├── service/          AuthService, TranslationService, VoiceTranslationService, ...
│       │   └── integration/
│       │       ├── speech/       SpeechToTextProvider + implementations
│       │       ├── translation/  TranslationProvider + implementations
│       │       └── tts/          TextToSpeechProvider + implementations
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/     Flyway V1__..., V2__...
│       └── test/                 JUnit 5 + Mockito; H2 (MySQL mode) + mocked providers
├── frontend/                     Static site, no framework, no build step
│   ├── *.html                    one file per page
│   ├── css/                      base.css (design tokens) + per-page sheets
│   ├── js/
│   │   ├── config.js             API base URL (no secrets)
│   │   ├── api/client.js         the only place that calls fetch()
│   │   ├── components/           reusable UI: language picker, toasts, recorder, player
│   │   └── pages/                one ES module per page
│   ├── assets/
│   └── dev-server.mjs            zero-dependency local static server
├── docs/                         architecture, checklist, API and testing docs, SQL setup
├── .env.example
├── Dockerfile, docker-compose.yml     (Phase 8)
└── README.md
```

## 3. Request flows

### Text translation (`POST /api/translations/text`)
1. `translator.js` validates the input (length, languages) and calls `api.translateText()`.
2. The JWT filter authenticates the request and the rate limiter checks the user's quota.
3. `TranslationService` validates again with Bean Validation, calls `TranslationProvider.translate(...)`, saves a `translations` row (type `TEXT`) and returns a `TranslationResponse` DTO.

### Voice translation (`POST /api/translations/voice`, multipart)
1. The browser asks for microphone permission (`getUserMedia`) and records with `MediaRecorder`, showing the elapsed time.
2. When recording stops, the audio blob is uploaded together with `sourceLanguage` and `targetLanguage`.
3. The backend checks the file's size, MIME type and magic bytes, then calls `SpeechToTextProvider.transcribe(...)` while the audio is held in memory only.
4. The recognised text goes through the same `TranslationProvider`, and a row with type `VOICE` is saved. **Raw audio is discarded and never persisted.**
5. The response contains `recognizedText`, `translatedText` and the detected language.
6. If the backend STT provider is not configured or does not support the language, the UI can use the browser's Web Speech API as a labelled fallback and then call the text endpoint.

### Text-to-speech (`POST /api/speech/synthesize`)
The backend calls `TextToSpeechProvider.synthesize(text, lang, voice, speed)` and streams `audio/mpeg` back. The browser plays it through an `<audio>` element, which gives play, pause, resume and stop. If no voice is available for the language, the API answers with a clear error and the UI offers `speechSynthesis` as a fallback.

## 4. Provider abstraction

```java
public interface TranslationProvider {
    TranslationResult translate(String text, String sourceLang /* nullable = auto */, String targetLang);
    Set<String> supportedLanguages();
}
public interface SpeechToTextProvider {
    TranscriptionResult transcribe(byte[] audio, AudioFormat format, String languageCode);
    Set<String> supportedLanguages();
}
public interface TextToSpeechProvider {
    SynthesizedAudio synthesize(String text, String languageCode, String voiceId, double speed);
    List<VoiceInfo> voicesFor(String languageCode);
}
```

- `@ConditionalOnProperty` picks the implementation from `TRANSLATION_PROVIDER`, `STT_PROVIDER` and `TTS_PROVIDER`.
- If a provider has no credentials, a `NotConfigured...Provider` is wired instead. It throws `ProviderNotConfiguredException`, which becomes **HTTP 503** with an explanatory message. It never returns invented translations.
- `GET /api/languages` builds the language list from the three providers' `supportedLanguages()`. For each language it reports `text`, `voiceInput` and `voiceOutput` separately, so the UI never offers voice for a language the configured provider cannot handle.
- Tests replace providers with Mockito mocks, so the test suite makes no paid API calls.

## 5. External services and credentials

| Capability | Candidate providers | What you need |
|---|---|---|
| Translation | Azure AI Translator · Google Cloud Translation · DeepL (no isiZulu/isiXhosa) · self-hosted LibreTranslate | API key (+ region for Azure) |
| Speech-to-Text | Azure AI Speech · Google Cloud Speech-to-Text · OpenAI Whisper API | API key / service-account JSON |
| Text-to-Speech | Azure AI Speech · Google Cloud Text-to-Speech · OpenAI TTS | API key / service-account JSON |
| Database | Local MySQL 8 (installed) | DB user + password in `.env` |
| Auth | none (self-issued JWT) | `JWT_SECRET` (≥ 256-bit random) |

**Recommendation:** use a single vendor for all three capabilities (Azure AI Speech + Translator, or Google Cloud). That means one account, one billing setup, and one set of credentials. Both have free tiers.

Before enabling a language for voice, we check the provider's current language list in Phase 3. The weakest area is expected to be isiXhosa speech (STT and TTS), and the language matrix will reflect whatever the provider actually supports.

## 6. Security design

- Passwords are hashed with BCrypt (cost 12). Emails are normalised to lower case, and a unique index enforces one account per email.
- Authentication uses stateless JWTs: HS256, 60-minute expiry, sent as `Authorization: Bearer`.
  - The API issues tokens with `JwtTokenService`. Spring Security's OAuth2 resource server validates them: signature, algorithm, expiry and issuer. No hand-written token filter is involved.
  - The `sub` claim is the user id. Controllers read it with `CurrentUser.id(jwt)`, and there are no `/{userId}` routes.
- The token is kept in `sessionStorage`, so it is cleared when the tab closes. A stored token is exposed to XSS, so:
  - the frontend never puts user data into `innerHTML`;
  - the pages ship with a strict Content-Security-Policy (`script-src 'self'`, `connect-src 'self'`).
- Same-origin API: the dev server, and Nginx in production, proxy `/api` to the backend, so the browser never makes cross-origin calls. CORS stays configured only for direct API access.
- Authorisation: every repository method that reads translations takes `userId`, and `findByIdAndUserId` returns **404**, not 403, so the response does not reveal that another user's record exists.
- Input validation uses Bean Validation on all DTOs. Text is capped at 5,000 characters. Language codes must match an allow-list.
- SQL injection is prevented by using only JPA and parameterised queries. No SQL is built by string concatenation.
- Uploads are capped at 10 MB and about 60 s of audio. Only whitelisted MIME types are accepted, with a magic-byte check.
- Rate limiting: a per-user token bucket on `/translations/*` and `/speech/*` returns 429 with `Retry-After`.
- Secrets come only from environment variables or `.env`, and `.gitignore` blocks committing them.

## 7. Technical risks

| Risk | Impact | Mitigation |
|---|---|---|
| Browser audio formats differ (Chrome/Firefox record WebM/Opus, Safari records MP4/AAC) | STT rejects the upload | Detect `MediaRecorder.isTypeSupported`. If needed, re-encode to 16 kHz mono WAV in the browser with the Web Audio API before uploading |
| Limited speech support for isiZulu/isiXhosa | Voice features missing for key local languages | Build the language matrix from provider capabilities. Offer text translation and the browser TTS fallback |
| Provider costs and quotas | Unexpected bills | Rate limiting, character and duration caps, free-tier keys during development |
| Microphone requires a secure context | Recording fails over plain HTTP | `localhost` counts as secure for development. Deploy behind HTTPS |
| JWT in browser storage | Token theft through XSS | CSP, no `innerHTML` with user data, short expiry |
| Port clash on the dev machine | Backend fails to start | Port 8080 is already used by another Java app on this machine, so LinguaVoice defaults to **8081** (`SERVER_PORT`) |
| No Docker installed on the dev machine | Phase 8 cannot be verified locally | Install Docker Desktop before Phase 8 |
