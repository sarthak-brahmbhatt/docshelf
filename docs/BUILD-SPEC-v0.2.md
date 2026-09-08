# DocShelf — Build Spec v0.2 (supersedes the scope of design v0.1; v0.1 remains the reference for insurance / ID / MF / reminders / chat internals)

## 0. What changed from v0.1

| Area | v0.1 | v0.2 |
|---|---|---|
| Input | PDFs of 3 known types | **Anything**: any MIME, any size (configurable cap, default 200 MB), photos from camera, typed text, voice notes |
| Types | 6 fixed | Fixed types kept (`INSURANCE_POLICY, MF_CAS, AADHAAR, PASSPORT, DRIVING_LICENCE, PAN_CARD`) + `PRESCRIPTION, MEDICAL_REPORT, BILL_RECEIPT, VOICE_NOTE, NOTE, OTHER`. Every document also gets a **generic** LLM extraction: title, summary, people, tags, key dates → auto reminders |
| Voice | none | Voice command button (speech → text → chat) and voice notes (audio → transcript → document). Chat can `create_reminder`; the client mirrors reminders into phone notifications / calendar |
| Medical | none | Prescription photo → doctor, visit date, follow-up date, medicines (dose, frequency, duration) → follow-up and refill reminders |
| Platform | Web only | Same Angular app as web PWA **and** Android via Capacitor. Backend runs on the Mac (LAN) or a server; app has a Settings screen for API URL + token |
| Auth | none | Static bearer token (`DOCSHELF_API_TOKEN`) because the API is now reachable from a phone. Still single-user |
| Images to LLM | never | Handwritten prescriptions and low-confidence OCR images are sent to OpenAI vision **as images**. Redaction is not possible on pixels; §7 states this |

## 1. Repo layout

```
docshelf/
  docker-compose.yml          postgres(pgvector) + mailpit + backend + frontend
  .env.example                every variable, with comments
  Makefile                    up / down / logs / backend-test / frontend-test / android
  README.md                   run instructions, LAN + Android setup, what is stubbed
  docs/                       design v0.1, this spec
  db/migration/V1__init.sql   THE schema (Flyway). Backend pom adds ../db/migration as a resource dir → classpath:db/migration
  backend/                    Spring Boot 3.3.x, Java 21, Maven (mvnw wrapper committed)
  frontend/                   Angular (standalone, Material), Capacitor (android/ generated, committed)
```

## 2. Backend

### 2.1 Conventions
- Package root `com.docshelf`. One package per module; inside: `XController`, `XService`, `XRepository`, `entity/`, `dto/`.
- Entities are JPA (`jakarta.persistence`), table/column names match `V1__init.sql` exactly. IDs `UUID` (`@Id @GeneratedValue(strategy = UUID)` or DB default). `TEXT+CHECK` enums map to Java enums with `@Enumerated(EnumType.STRING)`.
- DTOs are Java `record`s in `dto/`, never entities. Bean validation on request records. Mapping by hand (no MapStruct).
- Errors: `common/ApiExceptionHandler` (`@RestControllerAdvice`) → RFC 9457 `ProblemDetail` (Spring's class). `NotFoundException`(404), `ConflictException`(409), `GoneException`(410), `UnprocessableException`(422), `UpstreamException`(502), validation → 400 with `errors[]`.
- Auth: `config/ApiTokenFilter` — every `/api/**` request must carry `Authorization: Bearer <DOCSHELF_API_TOKEN>` or `X-Api-Token`. `/actuator/health` open. CORS open for `*` (token is the control).
- Config: `config/DocshelfProperties` (`@ConfigurationProperties(prefix="docshelf")`): `masterKey, apiToken, blobDir, maxUploadMb, openai.{apiKey, chatModel, extractModel, embeddingModel, transcriptionModel, visionEnabled}, mail.{from, to}, whatsapp.{provider=STUB|META, phoneNumberId, token, templateName}, timezone=Asia/Kolkata`.
- Time: `Clock` bean, injected everywhere (tests pin it). Zone `Asia/Kolkata`.
- Logging: never log field values, prompt text, chunk text, file bytes.
- Tests: JUnit 5 + Spring Boot Test + Testcontainers (`pgvector/pgvector:pg16`) via `AbstractIntegrationTest`. `FakeLlmClient`, `FakeEmbeddingClient`, `FakeTranscriptionClient`, `FakeVisionClient` are `@Primary` test beans that return canned JSON by purpose. One `@SpringBootTest` per controller hitting every endpoint with `MockMvc`. Unit tests for every extractor/parser with fixtures under `src/test/resources/fixtures/` (synthetic PDFs generated with PDFBox in a test helper when real samples are absent).

### 2.2 Package map and ownership

| Package | Contents | Built in |
|---|---|---|
| `config` | properties, `ApiTokenFilter`, CORS, `Clock`, Jackson, `@EnableScheduling`, `@EnableAsync`, OpenAI client bean | foundation |
| `common` | exceptions, handler, `PageResponse<T>`, `Ids` | foundation |
| `crypto` | `KeyService` (HKDF-SHA256 from master key → blobKek, fieldKek, hmacKey), `FieldCipher` (AES-256-GCM, AAD = table.column:id), `BlobStore` (per-doc DEK, AES-KW wrap, `DSH1|kekId|iv|ct|tag`), `Hmac`, `Masker` | foundation |
| `llm` | `LlmClient` (Responses API; `String complete(LlmRequest)` + `<T> T completeJson(LlmRequest, Class<T>)` with strict json_schema), `EmbeddingClient`, `TranscriptionClient`, `VisionClient` (image + prompt → json), OpenAI impls, `LlmCallLogService`, `Redactor` | foundation |
| `member`, `contact` | entities, repos | foundation (entities); controllers/services in **B7** |
| `document` | `Document`, `DocumentText`, `DocumentChunk`, `DocumentSummary`, `DocumentEvent`, `IngestionJob` entities + repos | foundation (entities) → **B1** (controller, service, worker, extractors, chunker, search) |
| `extract` | interfaces `TextExtractor`, `Classifier`, `FieldExtractor<T>`, `ExtractionResult`, `FieldMeta` | foundation (interfaces) → **B2** (`RuleClassifier`, `LlmClassifier`, `GenericExtractor`, `InsuranceExtractor`, `IdDocumentExtractor`, `Verhoeff`, `Mrz`), **B3** (`CasExtractor`), **B4** (`PrescriptionExtractor`) |
| `insurance`, `identity` | entities + repos | foundation |
| `medical` | `Prescription`, `Medicine` entities | foundation (entities) → **B4** (service, controller, reminder provider) |
| `portfolio` | entities | foundation (entities) → **B3** (`CasParser`, `AmfiNavClient`, `NavService`, `Xirr`, `PerformanceService`, controller) |
| `reminder` | entities, `ReminderSourceProvider` interface | foundation → **B5** (materializer, dispatcher, channels, controller, providers for policy/id/birthday/document_event) |
| `messaging` | `OutboundMessage` entity, `EmailGateway`, `WhatsAppGateway` + `StubWhatsAppGateway` + `MetaCloudWhatsAppGateway` (real HTTP shape, disabled unless provider=META) | foundation (entity, interfaces) → **B5** (impls) |
| `share` | `ShareService`, `ShareController` | **B7** |
| `chat` | entities; `ChatService` tool loop, `ToolRegistry`, `Tool` interface, tool impls, `PendingActionService`, controllers incl. `VoiceController` | foundation (entities) → **B6** |
| `audit` | `AuditLog` entity, `AuditService.record(...)` | foundation → **B7** (controller) |
| `system` | health + llm usage controller | **B7** |

### 2.3 Key interfaces (foundation writes these exactly)

```java
package com.docshelf.extract;
public interface TextExtractor {            // one per MIME family; B1 implements
  boolean supports(String mimeType);
  ExtractedText extract(byte[] bytes, String mimeType, ExtractionHints hints);   // pages[] of text, ocrUsed, charCount, meanOcrConfidence
}
public interface Classifier { Classification classify(ExtractedText text, ClassificationHints hints); } // type, confidence, source RULE|LLM
public interface FieldExtractor<T> {
  DocType type();
  ExtractionResult<T> extract(ExtractedText text, DocumentContext ctx);  // ctx: documentId, memberId?, imageBytes? (for vision), members roster
}
public record ExtractionResult<T>(T fields, Map<String,FieldMeta> meta, double confidence, List<String> reviewReasons, List<DocumentEventDraft> events) {}

package com.docshelf.llm;
public interface LlmClient {
  String complete(LlmRequest req);                       // purpose, system, user, maxTokens
  <T> T completeJson(LlmRequest req, Class<T> schemaType); // strict JSON schema derived from the record via Jackson
}
public interface EmbeddingClient { List<float[]> embed(List<String> texts); int dimensions(); }
public interface TranscriptionClient { Transcript transcribe(byte[] audio, String mimeType, String languageHint); }
public interface VisionClient { <T> T extractFromImage(byte[] image, String mimeType, String instructions, Class<T> schemaType); }
public final class Redactor { public Redaction redact(String text); }  // Redaction { String text; Map<String,String> placeholders; Map<String,Integer> counts; }

package com.docshelf.reminder;
public interface ReminderSourceProvider {
  String ruleKey();
  List<ReminderCandidate> candidates(LocalDate today);   // sourceType, sourceId, memberId, eventAt, title, body
}
```

### 2.4 Ingestion (B1) — status machine and extractor selection
`UPLOADED → NEEDS_PASSWORD? → EXTRACTING_TEXT → CLASSIFYING → EXTRACTING_FIELDS → INDEXING → READY | NEEDS_REVIEW | FAILED`

| MIME | TextExtractor | Notes |
|---|---|---|
| `application/pdf` | PDFBox text; OCR fallback (PDFBox render 300 DPI → tess4j) when mean chars/page < 200 | password candidates per v0.1 §1.2 |
| `image/*` (jpeg, png, webp, gif, bmp, tiff) | tess4j OCR (`eng`, `hin` if installed) | If OCR mean confidence < 60 or < 50 chars, or classification hint is medical → **vision path**: `VisionClient` with the image (B2/B4 extractors receive `ctx.imageBytes`) |
| `text/*`, `application/json`, `text/markdown`, `text/csv` | bytes as UTF-8 | typed text uses this (`POST /documents/text`) |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | Apache POI XWPF | |
| `audio/*` | `TranscriptionClient` → transcript | voice notes (`POST /documents/voice`) |
| anything else | no text; metadata-only; status `READY`, type `OTHER`, searchable by title/filename | still stored encrypted |

Chunking + embedding + hybrid search per v0.1 §1.2/§10. `SearchService.search(query, memberId?, docType?, limit)` → `List<SearchHit(documentId, title, docType, memberName, snippet, score)>`.

Every READY document also gets `GenericExtractor` output persisted to `document_summary` and `document_event`; events with `auto_reminder=true` create reminders via `ReminderService.createFromEvent(...)` (B5 exposes it; B1 calls it).

### 2.5 REST delta vs v0.1 §8 (everything in v0.1 §8 still applies; base `/api/v1`)

| Method | Path | Notes |
|---|---|---|
| POST | `/documents` | any MIME, ≤ `maxUploadMb`. Fields: `file`, `memberId?`, `typeHint?`, `title?`, `source?` (UPLOAD\|CAMERA) |
| POST | `/documents/text` | `{title?, text, memberId?}` → NOTE document |
| POST | `/documents/voice` | multipart `audio` (+`memberId?`) → VOICE_NOTE document, transcript indexed |
| GET | `/documents/{id}/summary` | generic summary + events |
| GET | `/documents/{id}/text` | redacted extracted text (pages) |
| POST | `/voice/transcribe` | multipart `audio` → `{text, language, durationSeconds}` (for voice commands; nothing stored) |
| GET/POST | `/reminders` | POST creates a user reminder `{title, dueAt (ISO datetime), notes?, memberId?, channels?}` |
| PUT | `/reminders/{id}` | edit title/dueAt/notes |
| GET | `/medical/prescriptions` | `memberId?` |
| GET | `/medical/prescriptions/{documentId}` | with medicines |
| GET | `/medical/medicines` | `memberId?`, `active=true` (end_date ≥ today) |
| PUT | `/medical/medicines/{id}` | corrections |
| GET | `/search` | `q, memberId?, docType?, limit?` → hits (same as chat tool) |
| GET/PUT | `/settings` | server-side user settings: `reminderEmail`, `ownerWhatsapp`, `voiceLanguage` |

Chat (`POST /chat/sessions/{id}/messages`) response gains `clientActions: [{type: "SCHEDULE_NOTIFICATION", reminderId, title, at} | {type: "ADD_CALENDAR_EVENT", title, startAt, endAt?, notes}]`. Tools added: `create_reminder(title, due_at, notes?, member_id?)`, `list_medicines(member_id?, active_only?)`, `save_note(text, title?)`, `get_document_summary(document_id)`. The five v0.1 tools stay; confirmation protocol for `share_document` / `get_field(reveal=true)` stays exactly as v0.1 §5.4.

## 3. Frontend

- Angular (latest CLI), standalone components, signals, Angular Material only, reactive forms. `provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))`.
- `core/api/` — `ApiService` with one typed method per endpoint, `models.ts` (all DTO interfaces), `api-base.ts` reads base URL + token from `SettingsService` (Capacitor Preferences on native, localStorage on web; default base URL `''` on web = same origin via nginx proxy, must be set on Android).
- `core/platform/` — thin services with web/native branches: `CameraService` (Capacitor Camera / `<input type=file capture>`), `SpeechService` (`@capacitor-community/speech-recognition` / Web Speech API), `RecorderService` (`capacitor-voice-recorder` / MediaRecorder), `NotificationService` (`@capacitor/local-notifications` / Web Notifications), `CalendarService` (`@ebarooni/capacitor-calendar` / disabled on web with a tooltip), `PlatformService.isNative()`.
- Features (`features/<name>`), each lazy-loaded: `shelf`, `document`, `family`, `reminders`, `portfolio`, `chat`, `audit`, `settings`. Component trees per v0.1 §9 plus: shelf gets a capture FAB (photo / file / type note / record voice note); chat gets a mic button (hold-to-talk → transcript into composer) and a voice-note button; reminders page has "Sync to phone" (schedules local notifications for the next 30 days, idempotent by reminder id) and per-reminder "Add to calendar".
- `LoadingState<T>` = `{status:'idle'|'loading'|'ready'|'error', data?, error?}` on every page; skeletons + error banner.
- Capacitor: `capacitor.config.ts` (`appId: com.docshelf.app`, `webDir: dist/frontend/browser`, `server.cleartext: true` for LAN http), `android/` generated and committed, permissions in `AndroidManifest.xml` (INTERNET, CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, READ/WRITE_CALENDAR). Scripts: `npm run build`, `npm run cap:sync`, `npm run android` (`cap open android`).

## 4. Docker
- `backend/Dockerfile`: multi-stage `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-jammy` + `tesseract-ocr tesseract-ocr-eng tesseract-ocr-hin`. Build context = repo root (needs `db/`).
- `frontend/Dockerfile`: `node:22` build → `nginx:alpine` with `nginx.conf` proxying `/api/` and `/actuator/` to `backend:8080`, `client_max_body_size 256m`.
- `docker-compose.yml`: `postgres` (`pgvector/pgvector:pg16`, healthcheck), `mailpit` (1025/8025), `backend` (8080, `depends_on: postgres: condition: service_healthy`, volume `blobs:/data/blobs`), `frontend` (4200:80). `.env` drives everything; sensible defaults so `docker compose up` works with only `OPENAI_API_KEY` missing (LLM features degrade to rule-only with a clear error, app still boots).

## 5. What is stubbed / degraded in v1
- WhatsApp: `StubWhatsAppGateway` (records `SIMULATED`). Meta impl exists but is off.
- CAMS/KFintech: file upload only; no API.
- Benchmark index history: CSV import; peer median otherwise.
- Push notifications: none. Local notifications are scheduled by the app when it syncs.
- Android APK: project generated; building requires Android Studio (not on this Mac).
- No sample PDFs yet: extractor tests use synthetic fixtures; regex weights will need recalibration on real files.
