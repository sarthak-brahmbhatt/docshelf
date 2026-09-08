# DocShelf

DocShelf is a self-hosted vault for a family's documents: insurance policies, mutual fund statements, Aadhaar / PAN / passport / driving licence, prescriptions and medical reports, bills, typed notes and voice notes. You drop anything in (PDF, photo from the phone camera, audio, text); it extracts the text (OCR when needed), classifies the document, pulls out the fields that matter, writes a short summary, and turns dates it finds into reminders (policy renewals, passport expiry, doctor follow-ups, medicine refills, birthdays). Everything at rest is encrypted under a master key that only you hold.

On top of the vault sits an assistant: a chat that answers questions from your own documents through tools (search, read a field, portfolio summary, create a reminder, save a note), with sensitive values masked and a confirm-in-the-UI step before anything is revealed or shared. The same Angular app runs as a web PWA on your Mac and as an Android app (Capacitor) on your phone, both talking to a Spring Boot backend on your home network. It is single-user, single-family, and designed to run on one machine with Docker Compose.

## Quick start

Prerequisites: Docker Desktop (Compose v2.24+). Nothing else is needed for the Docker path.

```bash
cp .env.example .env
make key                     # writes a fresh DOCSHELF_MASTER_KEY into .env
# optional: edit .env and set OPENAI_API_KEY and a DOCSHELF_API_TOKEN of your own
docker compose up --build    # or: make up   (detached)
```

First build takes a few minutes (Maven + npm inside Docker). Then:

| What | URL |
|---|---|
| App (web) | http://localhost:4200 |
| API | http://localhost:8080/api/v1 (health: http://localhost:8080/actuator/health) |
| Mailpit (captured e-mails) | http://localhost:8025 |
| Postgres | 127.0.0.1:5432, user/db `docshelf` (local machine only) |

The web app is served by nginx, which proxies `/api` and `/actuator` to the backend, so on the Mac the app needs no configuration. Open the app, add yourself under Family, and upload something.

## First-run checklist

1. **Master key** — `make key` did this. Copy the `DOCSHELF_MASTER_KEY` line from `.env` into your password manager now. If it is lost, every stored file and encrypted field is gone for good; backups will not help.
2. **API token** — `DOCSHELF_API_TOKEN` in `.env`. The example value works but is public; set your own (`openssl rand -hex 24`). The phone app needs this token.
3. **OPENAI_API_KEY** — optional. Set it to enable the AI features. Without it:
   - uploads, storage, OCR, download and sharing all work;
   - classification is rule-based only (regex on the extracted text), no LLM fallback;
   - generic summary, LLM field extraction, embeddings / semantic search and chat are disabled and the UI shows a clear "AI features need OPENAI_API_KEY" message instead of failing silently;
   - reminders still come from rule-based sources (dates found by regex, birthdays).
4. **Mail** — reminder e-mails go to Mailpit by default (nothing leaves the machine). To use a real SMTP server, change `MAIL_HOST` / `MAIL_PORT` and `DOCSHELF_MAIL_FROM` / `DOCSHELF_MAIL_TO`.
5. **Vision** — `DOCSHELF_VISION_ENABLED=true` sends handwritten prescriptions and unreadable scans to OpenAI as images. Set it to `false` if you do not want any image to leave the machine.

## LAN and Android setup

The backend port 8080 is published on all interfaces so a phone on the same Wi-Fi can reach it; access is gated by the API token.

1. Find the Mac's LAN IP: System Settings > Wi-Fi > Details, or `ipconfig getifaddr en0`.
2. On the Mac, check `http://<mac-ip>:8080/actuator/health` from the phone's browser. If it fails, allow Docker through the macOS firewall.
3. In the DocShelf app on the phone, open **Settings** and set
   - API URL: `http://<mac-ip>:8080`
   - API token: the `DOCSHELF_API_TOKEN` value from `.env`
4. The connection is plain HTTP on your home network (the Android build has `cleartext: true` for this). Do not port-forward 8080 to the internet; put a TLS reverse proxy or a VPN (Tailscale works well) in front if you want access from outside.

Building the Android app:

```bash
cd frontend && npm install
make android          # npm run cap:sync && npx cap open android  -> build/run from Android Studio
```

Android Studio and an Android SDK are required (not included). The web PWA is an alternative: open `http://<mac-ip>:4200` on the phone and "Add to home screen"; camera capture and notifications work there too, calendar sync does not.

## What is stubbed or degraded in v1

- **WhatsApp**: `WHATSAPP_PROVIDER=STUB` records a `SIMULATED` send and delivers nothing. The Meta Cloud API implementation exists behind `WHATSAPP_PROVIDER=META` but is untested against a live number.
- **CAMS / KFintech**: consolidated account statements are uploaded as files; there is no API pull.
- **Benchmark index history**: CSV import only; peer-median comparison otherwise.
- **Push notifications**: none. The app schedules local notifications when you press "Sync to phone" on the Reminders page.
- **Android APK**: project is generated and committed, but building needs Android Studio.
- **Extractors** were calibrated on synthetic fixtures; expect to correct fields on the first real documents (corrections are one click and are audited).

## Security notes

- **Master key** (`DOCSHELF_MASTER_KEY`): 32 random bytes. Derives (HKDF) the blob key, the field key and the HMAC key. Blobs are AES-256-GCM with a per-document key wrapped under the master key; sensitive fields (Aadhaar, PAN, passport, DL numbers, addresses, PDF passwords) are AES-256-GCM in the database; only masks (`XXXX XXXX 1234`) are stored in clear. Someone who copies the Postgres volume or the blobs volume without the key gets ciphertext, names, dates and masked numbers. Someone with the key and the disk gets everything. Keep `.env` mode 600 and out of git (it is in `.gitignore`).
- **API token** (`DOCSHELF_API_TOKEN`): the only authentication. Every `/api/**` call must send `Authorization: Bearer <token>` (or `X-Api-Token`). Rotate it by editing `.env` and `docker compose up -d backend`, then update the phone's Settings.
- **Reveal and audit**: API responses never contain a full sensitive value; a reveal is an explicit call, shown for 30 seconds, and written to the append-only audit log, as is every share and every original download.
- **What leaves the machine** (only when `OPENAI_API_KEY` is set):
  - *redacted text* of documents for classification, extraction, summaries and embeddings. The redactor replaces Aadhaar, VID, PAN, passport, DL, MRZ lines, phone numbers, e-mails and long digit runs with placeholders before every call; counts are recorded per call so you can audit that it ran. Names, dates of birth, policy numbers, amounts, scheme names and holdings *do* go, in redacted form: they are what extraction needs.
  - *chat turns*: the family roster (names and relations only), the conversation, and masked tool results.
  - *audio* of voice notes and voice commands for transcription.
  - *images*, when `DOCSHELF_VISION_ENABLED=true`: handwritten prescriptions and low-confidence scans are sent as pixels. Pixels cannot be redacted, so anything visible on that image (including an ID number if it is on the page) reaches OpenAI. Turn vision off if that is not acceptable.
  - OpenAI calls use `store: false`; that is a contractual control, not a technical one.
  - AMFI NAV download is a public file; e-mail goes to whatever `MAIL_HOST` is (Mailpit by default, so nowhere).
- Postgres and Mailpit are bound to `127.0.0.1`. Port 8080 is LAN-visible by design; change the mapping in `docker-compose.yml` to `127.0.0.1:8080:8080` for a web-only setup.
- FileVault (or equivalent disk encryption) on the host is assumed.

## Backup and restore

```bash
make backup                         # -> backups/<timestamp>/{docshelf.dump, blobs.tar.gz, MANIFEST.txt}
make restore DIR=backups/<timestamp>
```

`backup.sh` runs `pg_dump` inside the postgres container and tars the `blobs` volume through a throwaway container, so the backend does not have to be running. The backup deliberately does **not** contain `.env`: a backup without the master key is unreadable, and a backup with it is your whole vault in plain reach. Keep the key in a password manager and the backup directory somewhere else (external disk, encrypted cloud folder). `restore.sh` drops and recreates the database, restores the dump, replaces the volume contents and restarts the backend; it asks for confirmation first and requires the same master key to be present in `.env`.

## Local development (outside Docker)

```bash
make backend-run     # docker compose up -d postgres mailpit, then cd backend && ./mvnw spring-boot:run (Java 21)
make frontend-run    # cd frontend && ng serve with frontend/proxy.conf.json (/api -> http://localhost:8080)
make backend-test    # ./mvnw test (Testcontainers needs Docker)
make frontend-test   # ng test, headless
```

`backend-run` loads `.env` and overrides `POSTGRES_HOST` / `MAIL_HOST` to `localhost`; blobs go to `./data/blobs` (git-ignored). `make help` lists every target.

## Repository layout

```
docker-compose.yml        postgres (pgvector) + mailpit + backend + frontend
.env.example              every variable, with comments; copy to .env
Makefile                  up / down / logs / tests / local dev / android / backup / restore / key
scripts/                  gen-master-key.sh, backup.sh, restore.sh
db/migration/V1__init.sql the schema (Flyway; the backend mounts this directory as a resource)
backend/                  Spring Boot 3.x, Java 21, Maven (mvnw), backend/Dockerfile (multi-stage, context = repo root)
frontend/                 Angular standalone + Material, Capacitor android/, frontend/Dockerfile (nginx)
docs/                     design and build specs
backups/                  created by make backup (git-ignored)
```

## Documents

- [docs/BUILD-SPEC-v0.2.md](docs/BUILD-SPEC-v0.2.md): the build contract (scope, packages, REST delta, Docker).
- [docs/DocShelf-design-v0.1.md](docs/DocShelf-design-v0.1.md): the reference design (ingestion pipeline, data model, reminders, portfolio, chat protocol, sharing, security, full API contract, UI).
