# LinguaVoice — AI Language & Voice Translator

LinguaVoice is a full-stack web application for talking across languages. You speak or type, the AI translates, and you can listen to the result.

> **Status:** Phase 2 (database and authentication) is complete. See [docs/CHECKLIST.md](docs/CHECKLIST.md) for progress.

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

The `.env` file is git-ignored.

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
41 tests use an in-memory H2 database in MySQL mode and run the real Flyway migrations. They need no MySQL server and make no AI provider calls.

| Suite | Covers |
|---|---|
| `AuthControllerTest` | registration, BCrypt hashing, duplicate and case-insensitive emails, validation, login, identical errors for wrong password and unknown email, tampered/expired/foreign-issuer tokens, tokens of deleted accounts |
| `UserControllerTest` | profile read/update, email conflicts, password change, users only ever seeing their own data |
| `AuthServiceTest` | service logic with Mockito mocks, including the timing-attack defence and the registration race |
| `ConcurrentUserUpdateTest` | regression: overlapping profile and password updates must not overwrite each other |
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

Error statuses:
- **400** – validation failed (`fieldErrors` says which field and why).
- **401** – missing, invalid or expired token, or wrong credentials.
- **409** – the email address is already in use.

Passwords must be 8–72 characters and contain a letter and a number.

---
*Remaining README sections (features, environment variable reference, screenshots, Docker) are completed in Phase 8.*
