# LinguaVoice REST API

**Base URL:**
- Development: `http://localhost:5500/api` (through the dev server) or `http://localhost:8081/api` (direct).
- Docker: `http://localhost:8088/api`.

**Format:** JSON (UTF-8), except voice upload (multipart) and speech output (`audio/mpeg`). Times are ISO-8601 in UTC, e.g. `2026-11-24T08:31:12.402Z`.

## Authentication

**Sign in:** `POST /api/auth/register` or `POST /api/auth/login` returns an access token. Send it on every other request:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Token:**
- HS256-signed JWT; expires after 60 minutes (`JWT_EXPIRATION_MINUTES`).
- `sub` is the user id.
- There is no refresh token: the client signs in again.

**When a token stops working:** the API returns **401** if the token is missing, malformed, tampered with, expired, from another issuer, or belongs to a deleted account. It also returns 401 for a token **issued before the user's last password change**.

**Signing out:** client-side (discard the token).

## Errors

Every error has the same body:

```json
{
  "timestamp": "2026-11-24T08:31:12.402Z",
  "status": 400,
  "error": "Bad Request",
  "message": "One or more fields are invalid.",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "Enter a valid email address." }
}
```
`fieldErrors` is only present for field-level problems. Messages are safe to show to users; technical details are only logged on the server.

| Status | Meaning |
|---|---|
| 400 | Validation failed, or a malformed body or parameter |
| 401 | Not signed in, invalid or revoked token, or wrong email or password |
| 404 | Not found, **including another user's records** (so ids can't be probed) |
| 409 | Email address already in use |
| 413 | Upload larger than 10 MB |
| 415 | Unsupported content type |
| 422 | No speech could be recognised in the recording |
| 429 | Too many requests. AI endpoints: 30 per user per minute (`AI_RATE_LIMIT_PER_MINUTE`). Login: 5 failures per email in 15 minutes. See the `Retry-After` header (seconds) |
| 502 | The AI provider failed or timed out |
| 503 | No AI provider is configured, or its credentials were rejected |

## Endpoints

### Health
#### `GET /api/health` (public)
```json
{ "status": "UP", "application": "LinguaVoice", "version": "0.1.0-SNAPSHOT", "database": "UP", "timestamp": "…" }
```
Returns 503 with `"status": "DEGRADED"` when the database is unreachable.

### Authentication (public)
#### `POST /api/auth/register` → 201
```json
{ "fullName": "Ada Lovelace", "email": "ada@example.com", "password": "Secret123", "confirmPassword": "Secret123" }
```
**Rules:**
- name: 2–100 characters;
- email: valid, stored in lower case, unique;
- password: 8–72 characters, with a letter and a number.

**Response:** an **AuthResponse**:
```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-24T09:31:12Z",
  "user": {
    "id": 7, "fullName": "Ada Lovelace", "email": "ada@example.com", "createdAt": "…",
    "preferences": { "preferredSourceLanguage": "en", "preferredTargetLanguage": "af", "speechSpeed": 1.00 }
  }
}
```
**Errors:** 400 (validation, or passwords don't match), 409 (email taken).

#### `POST /api/auth/login` → 200
```json
{ "email": "ada@example.com", "password": "Secret123" }
```
**Response:** an AuthResponse.

**Errors:**
- **401** `"Invalid email or password."`: the same message and timing for an unknown email and for a wrong password.
- **429** after 5 failures for that email within 15 minutes.

### Account
#### `GET /api/users/profile` → 200
**Response:** the `user` object shown above.

#### `PUT /api/users/profile` → 200
```json
{ "fullName": "Ada King", "email": "countess@example.com" }
```
**Errors:** 409 if the email belongs to another account.

#### `PUT /api/users/preferences` → 200
```json
{ "preferredSourceLanguage": "auto", "preferredTargetLanguage": "zu", "speechSpeed": 1.25 }
```
**Rules:**
- source: a catalog code or `auto`;
- target: a catalog code different from the source;
- speed: 0.50–2.00, with at most 2 decimals.

#### `PUT /api/users/password` → 200
```json
{ "currentPassword": "Secret123", "newPassword": "NewSecret456", "confirmNewPassword": "NewSecret456" }
```
**Effect:** every previously issued token is revoked (other devices are signed out). The response is a fresh AuthResponse for this session.

**Errors:** 400 (wrong current password, mismatch, or unchanged password).

### Languages
#### `GET /api/languages` → 200
Lists the catalog languages **that the configured providers actually support**, with capabilities per feature:
```json
{
  "translationAvailable": true,
  "provider": "Azure AI Translator",
  "voiceInputAvailable": true,  "voiceInputProvider": "Azure AI Speech",
  "voiceOutputAvailable": true, "voiceOutputProvider": "Azure AI Speech",
  "languages": [
    { "code": "zu", "name": "isiZulu", "nativeName": "isiZulu", "textTranslation": true, "voiceInput": true, "voiceOutput": true },
    { "code": "xh", "name": "isiXhosa", "nativeName": "isiXhosa", "textTranslation": true, "voiceInput": false, "voiceOutput": false }
  ]
}
```
Without a translation provider, `translationAvailable` is `false` and `message` explains why.

The catalog: `en` English, `af` Afrikaans, `zu` isiZulu, `xh` isiXhosa, `es` Spanish, `fr` French, `pt` Portuguese, `de` German, `zh` Mandarin Chinese, `ar` Arabic, `ja` Japanese, `sw` Swahili.

### Translation
#### `POST /api/translations/text` → 201
```json
{ "text": "Good morning", "sourceLanguage": "auto", "targetLanguage": "zu" }
```
**Request:**
- `text`: 1–5000 characters.
- `sourceLanguage`: a code, or `auto` (or omitted) for detection.

**Response:** a **Translation**, which is saved to history:
```json
{
  "id": 42, "originalText": "Good morning", "translatedText": "Sawubona",
  "sourceLanguage": "en", "sourceDetected": true, "targetLanguage": "zu",
  "translationType": "TEXT", "createdAt": "…"
}
```
`sourceLanguage` is `und` when the provider can't detect the language.

**Errors:** 400, 429, 502, 503.

#### `POST /api/translations/voice` → 201 (multipart/form-data)
| Part | Description |
|---|---|
| `audio` | WAV recording: **16 kHz, mono, 16-bit PCM, 0.3–60 seconds**, at most 10 MB. The bytes are checked, not the file name or declared type. |
| `sourceLanguage` | the language spoken (must have `voiceInput: true`) |
| `targetLanguage` | the language to translate into |

**Response:** a Translation with `translationType: "VOICE"`, where `originalText` is the recognised speech. **The audio is never stored.**

**Errors:** 400 (bad audio or unsupported voice language), 422 (no speech recognised), 429, 502, 503.

```bash
curl -H "Authorization: Bearer $TOKEN" -F audio=@recording.wav -F sourceLanguage=en -F targetLanguage=zu \
     http://localhost:8081/api/translations/voice
```

### History
#### `GET /api/translations/history` → 200
| Query | Default | Description |
|---|---|---|
| `search` | | case-insensitive substring of the original or translated text (≤ 200 chars; `%` and `_` are literal) |
| `language` | | translations **from or into** this code |
| `type` | | `TEXT` or `VOICE` |
| `page` | 0 | zero-based |
| `size` | 20 | 1–50 |

**Response** (newest first): `{ "items": [Translation…], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }`

#### `GET /api/translations/{id}` → 200
**Response:** a Translation. 404 if it doesn't exist **or belongs to another user**.

#### `DELETE /api/translations/{id}` → 204
404 if it doesn't exist or belongs to another user.

#### `DELETE /api/translations/history` → 200
Deletes all of the caller's translations: `{ "deleted": 12 }`.

### Dashboard
#### `GET /api/dashboard/statistics?timezone=Africa/Johannesburg&days=14` → 200
`timezone`: an IANA zone (default UTC) used to decide which day a translation belongs to. `days`: 1–90.
```json
{
  "totalTranslations": 57, "textTranslations": 41, "voiceTranslations": 16, "lastSevenDays": 12,
  "topLanguages": [ { "code": "en", "name": "English", "count": 50, "share": 44 } ],
  "dailyActivity": [ { "date": "2026-11-11", "count": 0 }, … { "date": "2026-11-24", "count": 8 } ],
  "timeZone": "Africa/Johannesburg"
}
```
`topLanguages`: at most 5, counting each use as a source or a target. `share` is a percentage of all uses.

#### `GET /api/dashboard/recent-activity?limit=5` → 200
**Response:** the newest Translations (`limit` 1–20).

### Speech output
#### `GET /api/speech/voices?language=zu` → 200
```json
{ "language": "zu", "available": true, "provider": "Azure AI Speech",
  "voices": [ { "id": "zu-ZA-ThandoNeural", "name": "Thando", "locale": "zu-ZA", "gender": "Female" } ] }
```
`available: false` (with an empty list) means the server has no voice for that language; the frontend then uses the browser's voices.

#### `POST /api/speech/synthesize` → 200 `audio/mpeg`
```json
{ "text": "Sawubona", "language": "zu", "voice": "zu-ZA-ThandoNeural", "speed": 1.25 }
```
**Request:**
- `voice` is optional (the language's default voice is used);
- `speed` is 0.5–2.0 (default 1.0).

**Response:**
- MP3 audio, sent with `Cache-Control: no-store` and never stored;
- the voice used is in the `X-LinguaVoice-Voice` header.

**Errors:** 400 (unknown voice, no voice for the language, or text too long for the provider), 429, 502, 503.

## Security headers
Every API response includes:
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
- `Referrer-Policy: no-referrer`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Cache-Control: no-cache, no-store`
