# DocShelf backend (foundation)

Spring Boot 3.5.16 / Java 21 / Maven. PostgreSQL 16 + pgvector. Schema lives in `../db/migration/V1__init.sql`
and is mapped into the jar as `classpath:db/migration` by `pom.xml` (Flyway runs it on boot; Hibernate only
validates: `spring.jpa.hibernate.ddl-auto=validate`).

## Run

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home   # any Java 21
./mvnw -q -DskipTests package          # builds target/docshelf-backend-0.2.0-SNAPSHOT.jar
./mvnw test                            # unit tests + Testcontainers (needs Docker; image pgvector/pgvector:pg16)
java -jar target/docshelf-backend-0.2.0-SNAPSHOT.jar
```

Environment (all optional in dev, see `src/main/resources/application.yml`):

| Variable | Default | Meaning |
|---|---|---|
| `POSTGRES_HOST/PORT/DB/USER/PASSWORD` | localhost/5432/docshelf/docshelf/docshelf | datasource |
| `DOCSHELF_MASTER_KEY` | fixed **dev-only** key | base64 of 32 random bytes; derive with `openssl rand -base64 32` |
| `DOCSHELF_API_TOKEN` | `dev-token` | bearer token every `/api/**` call must present |
| `DOCSHELF_BLOB_DIR` | `./data/blobs` | encrypted file store |
| `DOCSHELF_MAX_UPLOAD_MB` | 200 | multipart limit |
| `OPENAI_API_KEY` | none | without it the app boots; LLM/embedding/vision/transcription calls throw `UpstreamException("OPENAI_API_KEY not configured")` |
| `OPENAI_CHAT_MODEL` / `OPENAI_EXTRACT_MODEL` / `OPENAI_EMBEDDING_MODEL` / `OPENAI_TRANSCRIPTION_MODEL` | gpt-4.1 / gpt-4.1-mini / text-embedding-3-small / gpt-4o-transcribe | models |
| `OPENAI_VISION_ENABLED` | true | image path on/off |
| `MAIL_HOST` / `MAIL_PORT` (+ `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`) | localhost / 1025 | SMTP (Mailpit in Compose) |
| `DOCSHELF_MAIL_FROM` / `DOCSHELF_MAIL_TO` | docshelf@localhost / empty | sender and default reminder recipient |
| `WHATSAPP_PROVIDER` (`STUB`\|`META`), `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_TOKEN`, `WHATSAPP_TEMPLATE_NAME` | STUB | WhatsApp gateway |
| `DOCSHELF_TIMEZONE` | Asia/Kolkata | `Clock` zone |

Docker: `backend/Dockerfile` (build context = repo root; installs tesseract eng+hin, `TESSDATA_PREFIX` set).

## Package map (foundation)

| Package | What is there | Owner of the rest |
|---|---|---|
| `com.docshelf.config` | `DocshelfProperties` (record, prefix `docshelf`), `ApiTokenFilter` (+ registration for `/api/*` in `WebConfig`, CORS `*`), `CoreConfig` (`Clock`, `ZoneId`, Jackson customiser, `@EnableScheduling`, `@EnableAsync`, executor bean `docshelfTaskExecutor`), `OpenAiConfig` (`OpenAIClient` bean) | foundation |
| `com.docshelf.common` | `DocshelfException` + `NotFoundException`(404) `ConflictException`(409) `GoneException`(410) `UnprocessableException`(422) `UpstreamException`(502, `retryable()`) `BadRequestException`(400) `ServiceUnavailableException`(503); `ApiExceptionHandler` (RFC 9457 `ProblemDetail`, validation -> 400 with `errors[]`, 413, 415); `PageResponse<T>`; `Ids`; `JobStatus`; `Verhoeff` | foundation |
| `com.docshelf.crypto` | `KeyService`, `FieldCipher`, `BlobStore`, `StoredBlob`, `Hmac`, `Masker`, `CryptoException` | foundation |
| `com.docshelf.llm` | interfaces `LlmClient`, `EmbeddingClient`, `TranscriptionClient`, `VisionClient`; records `LlmRequest`, `Transcript`, `Redaction`; `Redactor`; `JsonSchemaGenerator` (+ `@SchemaNullable`, `@SchemaDescription`); `OpenAi*Client` impls; `LlmCallLogService`; entity `LlmCall` + `LlmCallRepository` (`usageSince`) | foundation |
| `com.docshelf.extract` | `TextExtractor`, `Classifier`, `FieldExtractor<T>`, `ExtractionResult<T>`, `ExtractedText`, `ExtractionHints`, `ClassificationHints`, `Classification`, `DocumentContext`, `FieldMeta`, `DocumentEventDraft`, enums `DocType`, `DocStatus` | B1 / B2 / B3 / B4 add implementations here |
| `com.docshelf.member`, `contact`, `settings` | entities + repositories (`FamilyMember`, `Contact`, `UserSettings` with `UserSettingsRepository.get()`) | B7 controllers/services |
| `com.docshelf.document` | entities `Document`, `DocumentText`, `DocumentChunk`, `DocumentSummary`, `DocumentEvent`, `IngestionJob` (+ enums `DocSource`, `ClassifierSource`, `ExtractionSource`, `IngestionStep`, `EventKind`); repositories (incl. `IngestionJobRepository.claimable(now, limit)` with SKIP LOCKED); `ChunkSearchRepository` (JDBC: `insertChunk`, `deleteByDocument`, hybrid `search`) + `ChunkHit` | B1 |
| `com.docshelf.insurance`, `identity`, `medical`, `portfolio` | entities + repositories | B2 / B4 / B3 |
| `com.docshelf.reminder` | entities `ReminderRule`, `Reminder`; `ReminderSourceProvider`, `ReminderCandidate`; repositories (incl. `ReminderRepository.claimable`) | B5 |
| `com.docshelf.messaging` | entity `OutboundMessage`; `EmailGateway` / `EmailMessage` / `EmailSendResult`; `WhatsAppGateway` / `WhatsAppSendResult`; `StubWhatsAppGateway` (active when `docshelf.whatsapp.provider=STUB`, the default) | B5 implements `SmtpEmailGateway` and `MetaCloudWhatsAppGateway` (`@ConditionalOnProperty(... havingValue = "META")`) |
| `com.docshelf.chat` | entities `ChatSession`, `ChatMessage`, `PendingAction` + repositories | B6 |
| `com.docshelf.audit` | entity `AuditLog` (`@Immutable`), `AuditEvent` builder, `AuditService.record(...)` (REQUIRES_NEW, always inserts) | B7 controller |

There are **no business controllers** in the foundation. `src/test/.../testsupport/TestPingController` exists only for the filter test.

## How a feature agent adds a module

1. Create `com.docshelf.<module>` (`XController`, `XService`, `dto/` records with bean validation). Entities and repositories
   for your tables already exist; add finder methods to the existing repository interface if you need them.
2. Controllers live under `/api/v1/...`; the token filter already protects everything under `/api/`. Throw the
   `common` exceptions; `ApiExceptionHandler` turns them into ProblemDetail. Return `PageResponse.of(page)` for lists.
3. Inject `Clock` (never call `Instant.now()` / `LocalDate.now()` without it). Zone: the `ZoneId` bean or `clock.getZone()`.
4. Background work: `@Scheduled` methods work (scheduling is enabled; pool size 4) and `@Async("docshelfTaskExecutor")`.
5. Before any text goes to OpenAI, run `Redactor.redact(text)` and keep the `Redaction` to rehydrate placeholders.
   The OpenAI clients redact again defensively (idempotent) and record counts in `llm_call.redaction_counts`.
6. Sensitive fields: `FieldCipher.encrypt(value, FieldCipher.aad("id_document", "number", documentId))`,
   `Masker.forIdType(...)` for the display value, `Hmac.hmacHex(value)` for the `*_hmac` column.
7. Blobs: `BlobStore.store(bytes, documentId)` -> persist `storagePath`, `dekWrapped`, `kekId` on `Document`;
   `BlobStore.load(...)` to read; `storeWithExistingDek(...)` for the unlocked copy of a password PDF (same DEK).
8. Audit: `auditService.record(AuditEvent.of(AuditAction.REVEAL_FIELD, AuditOrigin.UI).document(id, label).field("number"))`.
9. Tests: extend `AbstractIntegrationTest` (shared pgvector container, profile `test`). Fakes are `@Primary`:
   `FakeResponseRegistry` (`stub(purpose, json)` / `stubObject(purpose, obj)`, `calls()`), `FakeLlmClient`,
   `FakeEmbeddingClient` (`embedOne(text)` is deterministic), `FakeTranscriptionClient` (purpose `transcribe`),
   `FakeVisionClient` (purpose `vision` or `vision:<SchemaType>`), `FakeEmailGateway` (`sent()`), `StubWhatsAppGateway.simulated()`.
   Do not edit `pom.xml`, `application*.yml`, or foundation files; report gaps to the orchestrator.

## Bean names and key signatures

```java
// llm
String     LlmClient.complete(LlmRequest req);                       // LlmRequest.of(purpose, system, user[, maxTokens]).withDocument(id)
<T> T      LlmClient.completeJson(LlmRequest req, Class<T> record);   // strict json_schema from JsonSchemaGenerator.generate(record)
List<float[]> EmbeddingClient.embed(List<String> texts); int dimensions();  // 1536
Transcript TranscriptionClient.transcribe(byte[] audio, String mimeType, String languageHint);
<T> T      VisionClient.extractFromImage(byte[] image, String mimeType, String instructions, Class<T> record);
Redaction  Redactor.redact(String text);   // Redaction{text, placeholders, counts}, rehydrate(String)
// model choice in OpenAiLlmClient: purpose starting with "chat" -> chat model, otherwise extract model
// schema rules: records only; List<T>, enums, nested records, LocalDate/UUID as string; Optional<T> or @SchemaNullable = nullable; Map unsupported

// crypto
byte[]  FieldCipher.encrypt(String plaintext, String aad); String decryptToString(byte[] blob, String aad);
StoredBlob BlobStore.store(byte[] plaintext, UUID documentId); byte[] load(String path, byte[] dekWrapped, String kekId, UUID documentId);
String  Hmac.hmacHex(String value);   Masker.aadhaar/pan/passport/drivingLicence/phone/email/vid/generic/forIdType

// search
long  ChunkSearchRepository.insertChunk(UUID documentId, int chunkIndex, Integer pageNo, String section, String text, float[] embedding);
List<ChunkHit> ChunkSearchRepository.search(float[] queryEmbedding, String queryText, UUID memberId, DocType docType, int limit);

// messaging
EmailSendResult    EmailGateway.send(EmailMessage m);        // EmailMessage.text(to, subject, body, dedupeKey); messageId() = <dedupeKey@docshelf.local>
WhatsAppSendResult WhatsAppGateway.sendDocument(String e164, String template, List<String> params, byte[] bytes, String filename);
WhatsAppSendResult WhatsAppGateway.sendText(String e164, String text);

// reminders
String ReminderSourceProvider.ruleKey(); List<ReminderCandidate> candidates(LocalDate today);
// ReminderCandidate(sourceType, sourceId, memberId, documentId, eventAt, title, body)

// audit
AuditLog AuditService.record(AuditEvent event | AuditEvent.Builder builder);
```

Bean names (default Spring naming): `docshelfProperties`, `clock`, `docshelfZone`, `docshelfTaskExecutor`,
`openAIClient`, `apiTokenFilterRegistration`, `keyService`, `fieldCipher`, `blobStore`, `hmac`, `masker`,
`redactor`, `openAiLlmClient`, `openAiEmbeddingClient`, `openAiTranscriptionClient`, `openAiVisionClient`,
`llmCallLogService`, `auditService`, `stubWhatsAppGateway`, `chunkSearchRepository`, plus one `<Entity>Repository` per table.

## Mapping notes

* IDs: UUID PKs use `@GeneratedValue(strategy = UUID)`; BIGSERIAL PKs use IDENTITY. Composite keys use `@IdClass`
  (`DocumentText.Key`, `NavHistory.Key`, `BenchmarkHistory.Key`, `SchemePerformance.Key`).
* Foreign keys are plain `UUID` columns (`memberId`, `documentId`, ...), no `@ManyToOne`, to keep services explicit.
* `TEXT + CHECK` enums -> `@Enumerated(STRING)`; `JSONB` -> `@JdbcTypeCode(SqlTypes.JSON)` on `Map`/`List<Map>`/`Map<String, FieldMeta>`;
  `TEXT[]`/`INT[]` -> `@JdbcTypeCode(SqlTypes.ARRAY)` on `List<String>`/`List<Integer>`; `CHAR(n)` -> `@JdbcTypeCode(SqlTypes.CHAR)`;
  `VECTOR(1536)` -> hibernate-vector `@JdbcTypeCode(SqlTypes.VECTOR) @Array(length = 1536) float[]`;
  `TIMESTAMPTZ` -> `Instant`; `document_chunk.tsv` (generated) is not mapped.
* `created_at`/`updated_at` use `@CreationTimestamp`/`@UpdateTimestamp`; DB defaults for NOT NULL columns are mirrored
  by Java field initialisers (`title = "New chat"`, `actor = "owner"`, `leadDays = [1, 0]`, ...).
* `audit_log` is append-only (trigger); the entity is `@Immutable`. `actuator/health` excludes the mail indicator.

## Versions

Spring Boot 3.5.16 (Hibernate 6.6.53.Final, Flyway 11.7.2, Jackson 2.21.4, Testcontainers 1.21.4, PostgreSQL JDBC 42.7.11),
hibernate-vector 6.6.53.Final, PDFBox 3.0.8 (+ pdfbox-io), tess4j 5.20.0, POI 5.5.1, openai-java 4.58.0,
commons-codec 1.18.0 (managed), commons-io 2.22.0, Maven wrapper 3.3.4 -> Maven 3.9.11.
