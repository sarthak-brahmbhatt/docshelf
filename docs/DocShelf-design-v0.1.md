# DocShelf — Design Draft **v0.1**

Status: DRAFT, awaiting "freeze v0.1". Date: 2026-09-08.

Conventions used throughout:

- `[CODE]` = deterministic Java, no network. `[LLM]` = OpenAI Responses API call (text leaves the machine). `[EMB]` = OpenAI embeddings call (text leaves the machine). `[EXT]` = other external call (AMFI, SMTP, Meta).
- Timezone is fixed to `Asia/Kolkata`. All IDs are UUIDs. Money is `NUMERIC`, never float.
- "Sensitive field" = Aadhaar number, passport number, driving-licence number, PAN, Aadhaar address. These are encrypted per field, masked by default, and every reveal is audited.

## 0. System shape

| Container | Image / runtime | Role |
|---|---|---|
| `frontend` | nginx + Angular build | Serves SPA, proxies `/api` → `backend` |
| `backend` | Spring Boot 3 / Java 21, PDFBox 3, tess4j + tesseract binary | REST API, ingestion worker, schedulers, chatbot |
| `postgres` | `pgvector/pgvector:pg16` | Relational data + chunk embeddings |
| `mailpit` | `axllent/mailpit` | Local SMTP catcher with web UI (http://localhost:8025) |

Volumes: `docshelf_blobs` (encrypted PDFs), `docshelf_pgdata`. Secrets via `.env`: `DOCSHELF_MASTER_KEY` (32 bytes base64), `OPENAI_API_KEY`, `SMTP_*`, `WHATSAPP_*` (ignored by the stub).

Single process, single user. Background work runs in the backend on a DB-backed job table (no broker).

---

## 1. Ingestion pipeline

### 1.1 Sequence

```mermaid
sequenceDiagram
  autonumber
  participant UI as Angular Shelf
  participant API as DocumentController
  participant BLOB as Blob store (encrypted)
  participant JOB as IngestionWorker
  participant PDF as PDFBox / Tesseract
  participant CLS as Classifier
  participant EXT as Field extractors
  participant RED as PII redactor
  participant OAI as OpenAI
  participant DB as PostgreSQL

  UI->>API: POST /documents (multipart PDF, member?, type hint?)
  API->>API: [CODE] sha256, size/mime checks, dedupe on sha256
  API->>BLOB: [CODE] new DEK, AES-256-GCM encrypt, write blob
  API->>DB: [CODE] insert document(status=UPLOADED), ingestion_job(QUEUED)
  API-->>UI: 202 {id, status}

  JOB->>DB: [CODE] claim job (FOR UPDATE SKIP LOCKED)
  JOB->>BLOB: [CODE] decrypt to memory
  JOB->>PDF: [CODE] open PDF
  alt password protected
    JOB->>JOB: [CODE] try candidate passwords (PAN, DOB, e-Aadhaar rule)
    alt none works
      JOB->>DB: status=NEEDS_PASSWORD
      Note over UI,JOB: user supplies password via POST /documents/{id}/password → job re-queued
    end
    JOB->>BLOB: [CODE] re-encrypt an unlocked copy as the working blob
  end
  JOB->>PDF: [CODE] PDFTextStripper (sort by position)
  alt too little text per page
    JOB->>PDF: [CODE] render 300 DPI → Tesseract OCR (eng[+hin])
  end
  JOB->>CLS: [CODE] rule-based classifier (keyword + pattern score)
  alt score < 0.8
    JOB->>RED: [CODE] redact first 2 pages
    RED->>OAI: [LLM] classify (json_schema, temperature 0)
    OAI-->>JOB: {type, confidence}
  end
  JOB->>DB: status=CLASSIFYING → EXTRACTING_FIELDS
  JOB->>EXT: dispatch by type
  Note over EXT: MF_CAS → [CODE] line parser<br/>PASSPORT → [CODE] MRZ parse<br/>AADHAAR/DL → [CODE] number+DOB regex, then [LLM] for name/address on redacted text<br/>INSURANCE → [CODE] regex pre-pass, then [LLM] structured extraction on redacted text
  EXT->>RED: [CODE] redact full text (typed placeholders)
  RED->>OAI: [LLM] extract (json_schema strict)
  OAI-->>EXT: typed JSON (contains placeholders, never real ID numbers)
  EXT->>EXT: [CODE] validate, rehydrate only allowed fields, score confidence
  EXT->>DB: [CODE] upsert insurance_policy | id_document | mf_* ; encrypt sensitive fields
  JOB->>DB: status=INDEXING
  JOB->>RED: [CODE] chunk (page-aware) + redact
  RED->>OAI: [EMB] text-embedding-3-small
  OAI-->>JOB: vectors
  JOB->>DB: [CODE] insert document_chunk(text, tsv, embedding)
  JOB->>DB: [CODE] materialize reminders for this document
  JOB->>DB: status=READY | NEEDS_REVIEW
  UI->>API: GET /documents/{id} (poll every 2s while not terminal)
```

### 1.2 Steps, ownership, and what is deterministic

| # | Step | Kind | Notes |
|---|---|---|---|
| 1 | Upload validation | `[CODE]` | PDF/JPEG/PNG only, ≤ 25 MB, sha256 dedupe → `409` with existing id |
| 2 | Encrypt at rest | `[CODE]` | Per-document DEK (AES-256-GCM), wrapped with KEK (AES-KW). See §7 |
| 3 | Password detection | `[CODE]` | `PDDocument.load` → `InvalidPasswordException`. Candidates per family member, in order: PAN upper, PAN lower, DOB `ddMMyyyy`, e-Aadhaar rule (first 4 letters of name upper + birth year), `ddMMyyyy` of DOB with member name prefix. If the user gave a type hint of `MF_CAS`, PANs are tried first. Successful password is stored encrypted on the document (needed only if the original must be re-opened) |
| 4 | Text extraction | `[CODE]` | PDFBox `PDFTextStripper` with `setSortByPosition(true)`; per-page text retained with page numbers |
| 5 | OCR fallback | `[CODE]` | Trigger: mean chars/page < 200 **or** < 40 % alphanumeric. PDFBox `PDFRenderer` at 300 DPI → tess4j (`eng`, optional `hin`). JPEG/PNG uploads always go through OCR. `ocr_used=true` lowers all confidences by 0.1 |
| 6 | Classification | `[CODE]` first, `[LLM]` fallback | Rule table in §1.3. Score ≥ 0.8 → done. Otherwise redacted first two pages to LLM with a fixed enum; LLM confidence < 0.6 → `UNKNOWN`, `NEEDS_REVIEW` (user picks type in UI, re-run extraction) |
| 7 | Field extraction | per type, §1.4 | Produces a typed record + per-field `{value, source: RULE\|LLM\|OCR, confidence}` |
| 8 | Validation & confidence | `[CODE]` | Date parse, numeric parse, checksum (Verhoeff for Aadhaar, ICAO check digits for MRZ, PAN format). Document confidence = mean of required-field confidences; `< 0.7` or any required field missing → `NEEDS_REVIEW` |
| 9 | Persist fields | `[CODE]` | Sensitive fields encrypted + masked + HMAC (for dedupe) before insert |
| 10 | Chunk + embed | `[CODE]` + `[EMB]` | ~1 000 chars, 150 overlap, sentence-boundary, page-aware. CAS: one chunk per folio/scheme block plus a portfolio summary chunk. Chunk text is the **redacted** text; raw ID numbers are never stored in `document_chunk` |
| 11 | Reminder materialization | `[CODE]` | §3, runs for the single document |
| 12 | Terminal status | `[CODE]` | `READY` or `NEEDS_REVIEW`; any exception after 3 attempts → `FAILED` with `last_error` |

Status machine: `UPLOADED → NEEDS_PASSWORD? → EXTRACTING_TEXT → CLASSIFYING → EXTRACTING_FIELDS → INDEXING → READY | NEEDS_REVIEW | FAILED`. `POST /documents/{id}/reprocess` restarts from `EXTRACTING_TEXT` (blob is kept). Every step is retried up to 3× with exponential backoff (1 s, 5 s, 25 s); OpenAI 429/5xx are retryable, 4xx are not.

### 1.3 Rule-based classifier

Score is the sum of matched signal weights, capped at 1.0. Any single signal marked ★ contributes 0.5.

| Type | Signals |
|---|---|
| `MF_CAS` | "Consolidated Account Statement" ★, "CAMS" or "KFintech"/"KFin", "Folio No", ≥ 3 ISINs matching `INF[0-9A-Z]{9}` ★ |
| `AADHAAR` | "Unique Identification Authority" ★, "Aadhaar"/"आधार", a 12-digit Verhoeff-valid number ★, "VID" |
| `PASSPORT` | MRZ line matching `^P<IND` ★, "Republic of India", "Passport No" |
| `DRIVING_LICENCE` | "Driving Licence"/"Driving License" ★, "DL No", "Transport"/"Non Transport", state pattern `[A-Z]{2}[0-9]{2}[ -]?[0-9]{4}[0-9]{7}` |
| `INSURANCE_POLICY` | "Policy No"/"Policy Number", "Sum Assured"/"Sum Insured" ★, "Premium", "Nominee", known insurer name list (LIC, HDFC Life, ICICI Lombard, Star Health, …) |
| `PAN_CARD` | "Income Tax Department" ★, "Permanent Account Number" ★, PAN pattern `[A-Z]{5}[0-9]{4}[A-Z]` |

### 1.4 Extraction per type

| Type | Deterministic part `[CODE]` | LLM part `[LLM]` | Required fields |
|---|---|---|---|
| `MF_CAS` | Entire parse (§4.1). No LLM. | none | ≥ 1 folio with ≥ 1 scheme and a closing balance |
| `PASSPORT` | MRZ (ICAO 9303 TD3): number, nationality, DOB, sex, expiry, surname/given names, all check digits verified | Only if MRZ unreadable after OCR: name, DOB, expiry from redacted text | number, name, DOB, expiry |
| `AADHAAR` | Number (12-digit + Verhoeff), DOB (`dd/MM/yyyy` or "Year of Birth"), gender keyword, VID | Name and address from redacted text (number already replaced by `⟨AADHAAR#1⟩`) | number, name, DOB or YOB |
| `DRIVING_LICENCE` | DL number (state pattern), DOB, "Valid Till"/"Validity (NT)"/"(TR)" dates | Name, issuing RTO, vehicle classes from redacted text | number, name, expiry |
| `INSURANCE_POLICY` | Policy number (regex near "Policy No"), amounts (`₹`/`Rs.`/`INR` numeric), all dates found | Structured extraction: insurer, policy_type, plan_name, sum_assured, premium_amount, premium_frequency, premium_due_date, policy_start, policy_expiry, nominee_name, nominee_relation, insured_members[] — regex candidates are passed as hints, LLM picks and labels | insurer, policy_no, sum_assured, policy_expiry |
| `PAN_CARD` | PAN, DOB, name (line above "Father's Name") | none | number, name |

LLM extraction mechanics: Responses API, `text.format = {type: json_schema, strict: true}`, `temperature 0`, `store: false`, `max_output_tokens 1500`. Input is the redacted text (§7.4) plus the regex hints. Output is validated against the schema; a field that fails parsing is dropped and the document is marked `NEEDS_REVIEW` rather than storing garbage. The LLM's output can only contain placeholders for ID numbers; the real number is written from the deterministic regex result. If the deterministic regex found no number, the field is empty and the user fills it in the UI.

---

## 2. Data model

### 2.1 Entity map

```
family_member 1─* document 1─1 insurance_policy
                            1─1 id_document
                            1─* document_chunk
                            1─* mf_transaction / mf_holding (via mf_folio)
family_member 1─* mf_folio 1─* mf_holding *─1 scheme_master 1─* nav_history
                                                           1─* scheme_performance
contact 1─* outbound_message
reminder_rule 1─* reminder *─1 outbound_message
chat_session 1─* chat_message 1─? pending_action
audit_log (append-only, no FKs so it survives deletes)
```

Design choices baked into the DDL:

- Enum-like columns are `TEXT` with `CHECK` constraints rather than PG `ENUM` types. Adding a value is a one-line Flyway migration instead of `ALTER TYPE` (rejected alternative: PG enums, harder to evolve, no benefit at this scale).
- `document` is the hub; per-type tables use `document_id` as primary key (1:1) so a document has exactly one typed record.
- `audit_log` has no foreign keys and a trigger that forbids `UPDATE`/`DELETE`.
- `embedding vector(1536)` matches `text-embedding-3-small`. Switching to a local 384-dim model (§10) is one migration.

### 2.2 DDL (`V1__init.sql`)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------- people ----------
CREATE TABLE family_member (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name       TEXT NOT NULL,
  relation        TEXT NOT NULL CHECK (relation IN
                    ('SELF','SPOUSE','MOTHER','FATHER','SON','DAUGHTER','BROTHER','SISTER','OTHER')),
  is_self         BOOLEAN NOT NULL DEFAULT FALSE,
  dob             DATE,
  gender          TEXT CHECK (gender IN ('M','F','O')),
  pan_encrypted   BYTEA,                 -- used as CAS password candidate; field-encrypted
  pan_masked      TEXT,                  -- e.g. XXXXX1234X
  pan_hmac        CHAR(64),
  email           TEXT,
  phone_e164      TEXT,
  notes           TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_family_member_self ON family_member ((TRUE)) WHERE is_self;

CREATE TABLE contact (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name       TEXT NOT NULL,
  relation        TEXT,
  email           TEXT,
  whatsapp_e164   TEXT,                  -- +91XXXXXXXXXX
  whatsapp_opted_in BOOLEAN NOT NULL DEFAULT FALSE,
  notes           TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (email IS NOT NULL OR whatsapp_e164 IS NOT NULL)
);

-- ---------- documents ----------
CREATE TABLE document (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  member_id             UUID REFERENCES family_member(id) ON DELETE SET NULL,
  doc_type              TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (doc_type IN
                          ('INSURANCE_POLICY','MF_CAS','AADHAAR','PASSPORT','DRIVING_LICENCE','PAN_CARD','OTHER','UNKNOWN')),
  title                 TEXT NOT NULL,
  status                TEXT NOT NULL CHECK (status IN
                          ('UPLOADED','NEEDS_PASSWORD','EXTRACTING_TEXT','CLASSIFYING','EXTRACTING_FIELDS',
                           'INDEXING','READY','NEEDS_REVIEW','FAILED')),
  original_filename     TEXT NOT NULL,
  mime_type             TEXT NOT NULL,
  size_bytes            BIGINT NOT NULL,
  sha256                CHAR(64) NOT NULL UNIQUE,
  storage_path          TEXT NOT NULL,                 -- relative path inside blob volume (original)
  unlocked_storage_path TEXT,                          -- password-removed working copy, if any
  dek_wrapped           BYTEA NOT NULL,                -- AES-KW(KEK, DEK)
  kek_id                TEXT NOT NULL,
  pdf_password_encrypted BYTEA,
  page_count            INT,
  ocr_used              BOOLEAN NOT NULL DEFAULT FALSE,
  text_chars            INT,
  classifier_source     TEXT CHECK (classifier_source IN ('RULE','LLM','USER')),
  classifier_confidence NUMERIC(4,3),
  extraction_source     TEXT CHECK (extraction_source IN ('RULE','LLM','MIXED','USER')),
  extraction_confidence NUMERIC(4,3),
  needs_review_reasons  TEXT[],
  last_error            TEXT,
  uploaded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at          TIMESTAMPTZ,
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_document_member ON document(member_id);
CREATE INDEX ix_document_type_status ON document(doc_type, status);

CREATE TABLE ingestion_job (
  id            BIGSERIAL PRIMARY KEY,
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  step          TEXT NOT NULL,          -- FULL | FROM_TEXT | FROM_FIELDS | INDEX_ONLY
  status        TEXT NOT NULL CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
  attempt       INT NOT NULL DEFAULT 0,
  run_after     TIMESTAMPTZ NOT NULL DEFAULT now(),
  error         TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ
);
CREATE INDEX ix_ingestion_job_pick ON ingestion_job(status, run_after) WHERE status = 'QUEUED';

CREATE TABLE document_chunk (
  id            BIGSERIAL PRIMARY KEY,
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  chunk_index   INT NOT NULL,
  page_no       INT,
  section       TEXT,                    -- e.g. 'FOLIO 1234567/89 · INF…' for CAS
  text          TEXT NOT NULL,           -- REDACTED text only
  tsv           TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', text)) STORED,
  embedding     VECTOR(1536),
  UNIQUE (document_id, chunk_index)
);
CREATE INDEX ix_chunk_tsv ON document_chunk USING gin(tsv);
CREATE INDEX ix_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

-- ---------- per-type field tables ----------
CREATE TABLE insurance_policy (
  document_id        UUID PRIMARY KEY REFERENCES document(id) ON DELETE CASCADE,
  member_id          UUID REFERENCES family_member(id) ON DELETE SET NULL,   -- policyholder
  insurer            TEXT,
  policy_no          TEXT,
  policy_type        TEXT CHECK (policy_type IN
                       ('HEALTH','LIFE_TERM','LIFE_ENDOWMENT','LIFE_ULIP','MOTOR','OTHER')),
  plan_name          TEXT,
  sum_assured        NUMERIC(14,2),
  premium_amount     NUMERIC(12,2),
  premium_frequency  TEXT CHECK (premium_frequency IN ('MONTHLY','QUARTERLY','HALF_YEARLY','YEARLY','SINGLE')),
  premium_due_date   DATE,               -- next due; rolled forward by the reminder engine
  policy_start       DATE,
  policy_expiry      DATE,
  nominee_name       TEXT,
  nominee_relation   TEXT,
  insured_members    JSONB NOT NULL DEFAULT '[]',  -- [{"memberId": uuid|null, "name": "..."}]
  field_meta         JSONB NOT NULL DEFAULT '{}',  -- {"sum_assured": {"source":"LLM","confidence":0.9,"page":2}, ...}
  raw_extraction     JSONB,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_policy_expiry ON insurance_policy(policy_expiry);
CREATE INDEX ix_policy_premium_due ON insurance_policy(premium_due_date);

CREATE TABLE id_document (
  document_id        UUID PRIMARY KEY REFERENCES document(id) ON DELETE CASCADE,
  member_id          UUID REFERENCES family_member(id) ON DELETE SET NULL,
  id_type            TEXT NOT NULL CHECK (id_type IN ('AADHAAR','PASSPORT','DRIVING_LICENCE','PAN_CARD','VOTER_ID')),
  number_encrypted   BYTEA,              -- AES-256-GCM, see §7
  number_masked      TEXT,               -- 'XXXX XXXX 1234'
  number_hmac        CHAR(64),           -- HMAC-SHA256 for dedupe / exact lookup
  name_on_document   TEXT,
  dob                DATE,
  year_of_birth      INT,
  gender             TEXT CHECK (gender IN ('M','F','O')),
  issue_date         DATE,
  expiry_date        DATE,
  issuing_authority  TEXT,
  address_encrypted  BYTEA,
  address_masked     TEXT,               -- city / state only
  field_meta         JSONB NOT NULL DEFAULT '{}',
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_id_document_expiry ON id_document(expiry_date);
CREATE INDEX ix_id_document_hmac ON id_document(number_hmac);

-- ---------- mutual funds ----------
CREATE TABLE scheme_master (
  isin              CHAR(12) PRIMARY KEY,
  amfi_code         TEXT,
  scheme_name       TEXT NOT NULL,
  amc               TEXT,
  scheme_type       TEXT CHECK (scheme_type IN ('OPEN_ENDED','CLOSE_ENDED','INTERVAL')),
  category          TEXT,                -- AMFI header, e.g. 'Equity Scheme - Mid Cap Fund'
  category_bucket   TEXT CHECK (category_bucket IN
                      ('LARGE_CAP','LARGE_MID_CAP','MID_CAP','SMALL_CAP','FLEXI_MULTI_CAP','OTHER_EQUITY',
                       'DEBT','HYBRID','INDEX_ETF','SOLUTION','OTHER')),
  benchmark_index   TEXT,                -- from category → index seed map; user-overridable
  plan              TEXT CHECK (plan IN ('DIRECT','REGULAR')),
  option_type       TEXT CHECK (option_type IN ('GROWTH','IDCW_PAYOUT','IDCW_REINVEST')),
  source            TEXT NOT NULL DEFAULT 'AMFI' CHECK (source IN ('AMFI','CAS','USER')),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_scheme_category ON scheme_master(category);

CREATE TABLE mf_folio (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  member_id          UUID REFERENCES family_member(id) ON DELETE SET NULL,
  folio_no           TEXT NOT NULL,
  amc                TEXT NOT NULL,
  pan_masked         TEXT,
  source_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (folio_no, amc)
);

CREATE TABLE mf_holding (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  folio_id           UUID NOT NULL REFERENCES mf_folio(id) ON DELETE CASCADE,
  isin               CHAR(12) NOT NULL REFERENCES scheme_master(isin),
  units              NUMERIC(18,3) NOT NULL,
  cost_value         NUMERIC(16,2),      -- from CAS when present, else Σ purchase amounts
  nav                NUMERIC(12,4),
  nav_date           DATE,
  value              NUMERIC(16,2),      -- units × nav, refreshed nightly
  as_of_date         DATE NOT NULL,      -- statement date of the CAS that produced this row
  source_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (folio_id, isin)
);

CREATE TABLE mf_transaction (
  id                 BIGSERIAL PRIMARY KEY,
  folio_id           UUID NOT NULL REFERENCES mf_folio(id) ON DELETE CASCADE,
  isin               CHAR(12) NOT NULL REFERENCES scheme_master(isin),
  txn_date           DATE NOT NULL,
  description        TEXT NOT NULL,
  txn_type           TEXT NOT NULL CHECK (txn_type IN
                       ('PURCHASE','PURCHASE_SIP','REDEMPTION','SWITCH_IN','SWITCH_OUT',
                        'IDCW_PAYOUT','IDCW_REINVEST','STAMP_DUTY','STT_TAX','SEGREGATION','MISC')),
  amount             NUMERIC(16,2),      -- signed: outflow from investor negative
  units              NUMERIC(18,3),      -- signed
  nav                NUMERIC(12,4),
  balance_units      NUMERIC(18,3),
  dedupe_key         CHAR(64) NOT NULL UNIQUE,  -- sha256(folio|isin|date|amount|units|description)
  source_document_id UUID REFERENCES document(id) ON DELETE SET NULL
);
CREATE INDEX ix_mf_txn_scheme ON mf_transaction(folio_id, isin, txn_date);

CREATE TABLE nav_history (
  isin       CHAR(12) NOT NULL REFERENCES scheme_master(isin),
  nav_date   DATE NOT NULL,
  nav        NUMERIC(12,4) NOT NULL,
  PRIMARY KEY (isin, nav_date)
);

CREATE TABLE benchmark_history (
  index_name  TEXT NOT NULL,             -- 'NIFTY 100 TRI'
  price_date  DATE NOT NULL,
  value       NUMERIC(16,4) NOT NULL,
  source      TEXT NOT NULL DEFAULT 'CSV_IMPORT',
  PRIMARY KEY (index_name, price_date)
);

CREATE TABLE scheme_performance (
  isin              CHAR(12) NOT NULL REFERENCES scheme_master(isin),
  as_of_date        DATE NOT NULL,
  ret_1y            NUMERIC(8,5),
  cagr_3y           NUMERIC(8,5),
  bench_ret_1y      NUMERIC(8,5),
  bench_cagr_3y     NUMERIC(8,5),
  benchmark_name    TEXT,
  benchmark_basis   TEXT NOT NULL CHECK (benchmark_basis IN ('INDEX','PEER_MEDIAN','NOT_APPLICABLE')),
  peer_count        INT,
  underperformed    TEXT NOT NULL CHECK (underperformed IN ('YES','NO','INSUFFICIENT_DATA','NOT_APPLICABLE')),
  computed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (isin, as_of_date)
);

-- ---------- reminders & messaging ----------
CREATE TABLE reminder_rule (
  rule_key     TEXT PRIMARY KEY,         -- POLICY_EXPIRY, PREMIUM_DUE, PASSPORT_EXPIRY, LICENCE_EXPIRY, BIRTHDAY
  source_type  TEXT NOT NULL CHECK (source_type IN ('DOCUMENT','FAMILY_MEMBER')),
  doc_type     TEXT,                     -- NULL for FAMILY_MEMBER rules
  date_field   TEXT NOT NULL,            -- column the event date comes from
  offsets_days INT[] NOT NULL,           -- {30,7,1}
  channels     TEXT[] NOT NULL,          -- {IN_APP,EMAIL}
  enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  description  TEXT
);

CREATE TABLE outbound_message (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel              TEXT NOT NULL CHECK (channel IN ('EMAIL','WHATSAPP')),
  provider             TEXT NOT NULL CHECK (provider IN ('SMTP','META_CLOUD','STUB')),
  recipient            TEXT NOT NULL,    -- email or E.164
  contact_id           UUID REFERENCES contact(id) ON DELETE SET NULL,
  subject              TEXT,
  template_name        TEXT,
  payload              JSONB NOT NULL,   -- body / template params (no secrets)
  attachment_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
  status               TEXT NOT NULL CHECK (status IN ('QUEUED','SENDING','SENT','FAILED','SIMULATED','UNKNOWN')),
  provider_message_id  TEXT,
  attempts             INT NOT NULL DEFAULT 0,
  last_error           TEXT,
  dedupe_key           TEXT NOT NULL UNIQUE,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at              TIMESTAMPTZ
);

CREATE TABLE reminder (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_key            TEXT NOT NULL REFERENCES reminder_rule(rule_key),
  source_type         TEXT NOT NULL CHECK (source_type IN ('DOCUMENT','FAMILY_MEMBER')),
  source_id           UUID NOT NULL,     -- document.id or family_member.id
  member_id           UUID REFERENCES family_member(id) ON DELETE SET NULL,
  event_date          DATE NOT NULL,     -- the expiry / due / birthday date
  offset_days         INT NOT NULL,
  fire_date           DATE NOT NULL,     -- event_date - offset_days
  channel             TEXT NOT NULL CHECK (channel IN ('IN_APP','EMAIL','WHATSAPP')),
  status              TEXT NOT NULL CHECK (status IN
                        ('PENDING','SENDING','SENT','FAILED','DISMISSED','SNOOZED','SUPERSEDED','EXPIRED','UNKNOWN')),
  title               TEXT NOT NULL,
  body                TEXT NOT NULL,
  dedupe_key          TEXT NOT NULL UNIQUE,   -- rule_key|source_id|event_date|offset_days|channel
  attempts            INT NOT NULL DEFAULT 0,
  claimed_at          TIMESTAMPTZ,
  sent_at             TIMESTAMPTZ,
  outbound_message_id UUID REFERENCES outbound_message(id) ON DELETE SET NULL,
  error               TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reminder_dispatch ON reminder(status, fire_date) WHERE status IN ('PENDING','SNOOZED');
CREATE INDEX ix_reminder_source ON reminder(source_type, source_id);

-- ---------- chat ----------
CREATE TABLE chat_session (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title            TEXT NOT NULL DEFAULT 'New chat',
  model            TEXT NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_message_at  TIMESTAMPTZ
);

CREATE TABLE chat_message (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id     UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
  seq            INT NOT NULL,
  role           TEXT NOT NULL CHECK (role IN ('USER','ASSISTANT','TOOL','DEVELOPER')),
  content        TEXT,                   -- assistant/user text, or tool result JSON (masked)
  tool_calls     JSONB,                  -- [{callId, name, arguments}]
  tool_call_id   TEXT,                   -- for role=TOOL
  tool_name      TEXT,
  input_tokens   INT,
  output_tokens  INT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, seq)
);

CREATE TABLE pending_action (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id    UUID REFERENCES chat_session(id) ON DELETE CASCADE,
  message_id    UUID REFERENCES chat_message(id) ON DELETE SET NULL,
  action_type   TEXT NOT NULL CHECK (action_type IN ('SHARE_DOCUMENT','REVEAL_FIELD')),
  payload       JSONB NOT NULL,          -- {documentId, contactId, channel} | {documentId, field}
  summary       TEXT NOT NULL,           -- human-readable, masked
  status        TEXT NOT NULL CHECK (status IN ('PENDING','EXECUTED','CANCELLED','EXPIRED','FAILED')),
  result        JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,    -- created_at + 10 min
  resolved_at   TIMESTAMPTZ
);

-- ---------- audit & LLM accounting ----------
CREATE TABLE audit_log (
  id             BIGSERIAL PRIMARY KEY,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor          TEXT NOT NULL DEFAULT 'owner',
  origin         TEXT NOT NULL CHECK (origin IN ('UI','CHAT','SYSTEM')),
  action         TEXT NOT NULL CHECK (action IN
                   ('UPLOAD_DOCUMENT','DELETE_DOCUMENT','DOWNLOAD_ORIGINAL','REVEAL_FIELD','SHARE_DOCUMENT',
                    'FIELD_EDIT','PENDING_ACTION_CREATED','PENDING_ACTION_CONFIRMED','PENDING_ACTION_CANCELLED',
                    'LLM_CALL','REMINDER_SENT')),
  document_id    UUID,                   -- no FK: must outlive the document
  document_label TEXT,                   -- snapshot: 'Aadhaar — Sunita'
  member_id      UUID,
  contact_id     UUID,
  contact_label  TEXT,
  field_name     TEXT,
  channel        TEXT,
  session_id     UUID,
  pending_action_id UUID,
  details        JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_audit_time ON audit_log(occurred_at DESC);
CREATE INDEX ix_audit_document ON audit_log(document_id);

CREATE OR REPLACE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'audit_log is append-only'; END $$;
CREATE TRIGGER trg_audit_log_immutable
  BEFORE UPDATE OR DELETE ON audit_log FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE TABLE llm_call (
  id               BIGSERIAL PRIMARY KEY,
  purpose          TEXT NOT NULL,        -- CLASSIFY | EXTRACT_INSURANCE | EXTRACT_ID | EMBED | CHAT
  model            TEXT NOT NULL,
  document_id      UUID,
  session_id       UUID,
  input_tokens     INT,
  output_tokens    INT,
  redaction_counts JSONB,                -- {"AADHAAR":1,"PAN":2,"PHONE":1}
  latency_ms       INT,
  status           TEXT NOT NULL,        -- OK | ERROR
  openai_response_id TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- seed ----------
INSERT INTO reminder_rule (rule_key, source_type, doc_type, date_field, offsets_days, channels, description) VALUES
 ('POLICY_EXPIRY',   'DOCUMENT',      'INSURANCE_POLICY', 'policy_expiry',    '{30,7,1}', '{IN_APP,EMAIL}', 'Insurance policy expiry'),
 ('PREMIUM_DUE',     'DOCUMENT',      'INSURANCE_POLICY', 'premium_due_date', '{7,1}',    '{IN_APP,EMAIL}', 'Premium due'),
 ('PASSPORT_EXPIRY', 'DOCUMENT',      'PASSPORT',         'expiry_date',      '{90,30}',  '{IN_APP,EMAIL}', 'Passport expiry'),
 ('LICENCE_EXPIRY',  'DOCUMENT',      'DRIVING_LICENCE',  'expiry_date',      '{90,30}',  '{IN_APP,EMAIL}', 'Driving licence expiry'),
 ('BIRTHDAY',        'FAMILY_MEMBER', NULL,               'dob',              '{7,0}',    '{IN_APP}',       'Family birthday');

INSERT INTO family_member (full_name, relation, is_self) VALUES ('Owner', 'SELF', TRUE);
```

Offsets for `PREMIUM_DUE` (7/1) and `BIRTHDAY` (7/0) are my choice; you specified only the others. See open question 6.

---

## 3. Reminder engine

Two scheduled phases plus event-driven hooks. Everything is keyed on `reminder.dedupe_key`, which is unique, so both phases can be re-run any number of times.

### 3.1 Rules (data-driven, `reminder_rule`)

| rule_key | Source | Event date | Offsets (days before) | Default channels |
|---|---|---|---|---|
| `POLICY_EXPIRY` | `insurance_policy.policy_expiry` | policy expiry | 30, 7, 1 | in-app, email |
| `PREMIUM_DUE` | `insurance_policy.premium_due_date` | next premium due; after it passes, rolled forward by `premium_frequency` (SINGLE → never) | 7, 1 | in-app, email |
| `PASSPORT_EXPIRY` | `id_document.expiry_date` where `id_type=PASSPORT` | expiry | 90, 30 | in-app, email |
| `LICENCE_EXPIRY` | `id_document.expiry_date` where `id_type=DRIVING_LICENCE` | expiry | 90, 30 | in-app, email |
| `BIRTHDAY` | `family_member.dob` | next anniversary (29 Feb → 28 Feb in non-leap years) | 7, 0 | in-app |

Channels are per rule and editable in the UI. `WHATSAPP` for reminders goes to the owner's own number (stub in v1). Aadhaar has no expiry; no rule.

### 3.2 Phase A — materialize (`0 0 6 * * *` IST, and synchronously after any document reaches `READY`, any field edit, or any member DOB change)

```
for rule in enabled rules:
  for src in sources(rule):                       -- one SQL per rule
    event = nextEventDate(src, rule, today)       -- null if no date, or SINGLE premium in the past
    if event is null: supersedePending(rule, src); continue
    wanted = {}
    for offset in rule.offsets_days:
      fire = event - offset
      if fire < today - 1: continue                -- missed by more than a day → don't create
      for ch in rule.channels:
        key = rule|src.id|event|offset|ch
        wanted += key
        INSERT INTO reminder (...) VALUES (..., status='PENDING')
          ON CONFLICT (dedupe_key) DO NOTHING
    -- stale rows (date changed on re-extraction or user edit)
    UPDATE reminder SET status='SUPERSEDED'
      WHERE rule_key=rule AND source_id=src.id AND status IN ('PENDING','SNOOZED')
        AND dedupe_key NOT IN wanted
-- collapse bursts: if several offsets for the same (rule, source, event, channel) are all due today
-- (app was down), keep the smallest offset, mark the rest SUPERSEDED
-- expire: PENDING rows whose event_date < today → EXPIRED
```

`nextEventDate` is pure Java, unit-tested per rule (premium roll-forward, leap-day birthdays, SINGLE premium).

### 3.3 Phase B — dispatch (`0 */15 * * * *`)

```
BEGIN
  rows = SELECT * FROM reminder
         WHERE status IN ('PENDING','SNOOZED') AND fire_date <= today
         ORDER BY fire_date LIMIT 50 FOR UPDATE SKIP LOCKED
  UPDATE reminder SET status='SENDING', claimed_at=now(), attempts=attempts+1 WHERE id IN (rows)
COMMIT
for r in rows:
  try:
    msgId = channelAdapter(r.channel).send(r, idempotencyKey = r.dedupe_key)
    UPDATE reminder SET status='SENT', sent_at=now(), outbound_message_id=msgId WHERE id=r.id AND status='SENDING'
    audit(REMINDER_SENT)
  catch retryable:
    UPDATE reminder SET status = CASE WHEN attempts < 3 THEN 'PENDING' ELSE 'FAILED' END, error=... WHERE id=r.id
  catch permanent:
    UPDATE reminder SET status='FAILED', error=... WHERE id=r.id
```

Idempotency guarantees, layer by layer:

1. **Materialize** is a pure upsert on `dedupe_key`; re-running creates nothing new.
2. **Dispatch** claims rows with a status transition inside a transaction (`PENDING → SENDING`). Two overlapping runs cannot both claim the same row (`SKIP LOCKED` + the `WHERE status='SENDING'` guard on the success update).
3. **Channel-level**: `IN_APP` is the reminder row itself (nothing to send, marking `SENT` is the delivery). `EMAIL` sets the RFC 5322 `Message-ID` to `<dedupe_key@docshelf.local>` and records an `outbound_message` row with the same `dedupe_key` (unique), so a retry after a crash finds the existing row and does not re-send. `WHATSAPP` has no provider-side idempotency key, so the adapter inserts `outbound_message` first (unique key), then calls the API; if the row already exists with `SENT` or `SENDING`, it does not call again.
4. **Crash between send and mark**: on startup a sweep moves `SENDING` rows older than 10 minutes to `UNKNOWN`. They are never auto-resent; the Reminders screen shows them with a manual "resend" button. This is deliberate at-most-once behaviour for external channels.
5. **Snooze** = `status='SNOOZED', fire_date=<chosen>`, same row, same key. **Dismiss** = `DISMISSED`, never re-created because the key still exists.

### 3.4 Message content

Title/body are rendered from a Java template at materialize time (so the UI can show them) and re-rendered at dispatch with current data. Bodies never contain full ID numbers; they use the masked value and a deep link to the document.

---

## 4. Portfolio module

### 4.1 CAS parsing `[CODE]`

Supports CAMS and KFintech **detailed** CAS (transaction-level) and **summary** CAS (holdings only). Parser is a line state machine over the position-sorted PDFBox text:

| State | Trigger (regex, simplified) | Emits |
|---|---|---|
| `HEADER` | `Consolidated Account Statement` … `(dd-MMM-yyyy)\s+To\s+(dd-MMM-yyyy)` | statement period, as-of date |
| `INVESTOR` | `PAN:\s*([A-Z]{5}\d{4}[A-Z])`, email, name block | investor (matched to family member by PAN HMAC, else by name) |
| `FOLIO` | `Folio No:\s*(\S+)` (+ `PAN:`/`KYC:` on same line) | folio |
| `SCHEME` | line containing `INF[0-9A-Z]{9}` and an AMFI code `\b\d{5,6}\b`; scheme name may wrap to next line — joined until `Registrar` or `ISIN` token | scheme (isin, amfi code, name, amc from preceding AMC header) |
| `OPENING` | `Opening Unit Balance:\s*([\d,.]+)` | opening units |
| `TXN` | `^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.+?)\s+(\(?[\d,.]+\)?)\s+(\(?[\d,.]+\)?)\s+([\d,.]+)\s+([\d,.]+)$` → date, description, amount, units, nav, balance. Parentheses = negative. Rows with only amount (stamp duty, STT) have no units/nav | transaction |
| `CLOSING` | `Closing Unit Balance:\s*([\d,.]+)\s+NAV on (dd-MMM-yyyy):\s*INR\s*([\d,.]+)\s+.*Valuation.*INR\s*([\d,.]+)` (Cost Value when present) | holding snapshot |

Transaction type is classified from the description with an ordered keyword table (`Systematic`→`PURCHASE_SIP`, `Redemption`→`REDEMPTION`, `Switch In/Out`, `IDCW`/`Dividend Reinvest`, `Stamp Duty`, `STT`, `Segregat`). Unknown → `MISC` and the document is flagged `NEEDS_REVIEW`.

Reconciliation `[CODE]`: for each scheme, `opening + Σ units == closing` within 0.001; a mismatch flags `NEEDS_REVIEW` with the scheme name. Summary CAS has no transactions → holdings only, XIRR shown as "needs detailed CAS".

Persistence: `mf_folio` upsert on `(folio_no, amc)`; `mf_transaction` insert with `ON CONFLICT (dedupe_key) DO NOTHING` (so overlapping CAS periods never double count); `mf_holding` upsert on `(folio_id, isin)` only if the new statement's `as_of_date` ≥ the stored one. `scheme_master` gets a stub row from the CAS (`source='CAS'`) if AMFI has not been loaded yet.

### 4.2 NAV refresh `[EXT]` (`0 30 23 * * *` IST, retry ×3 every 30 min, plus manual button)

Source: `https://www.amfiindia.com/spages/NAVAll.txt` (semicolon-separated: `Scheme Code;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date`, with section header lines like `Open Ended Schemes(Equity Scheme - Mid Cap Fund)` and AMC name lines). One download per night, ~11 000 rows:

1. Upsert every row into `scheme_master` (name, AMC, `category` from the section header, `category_bucket` via a static map of AMFI category → bucket, `plan`/`option_type` inferred from scheme name tokens "Direct"/"Regular"/"Growth"/"IDCW").
2. Insert into `nav_history` for **all** schemes (`ON CONFLICT DO NOTHING`). Storing everything (~4 M rows/year) is what makes peer-median benchmarking possible without another data source.
3. Update `mf_holding.nav/nav_date/value` for held ISINs.
4. Recompute `scheme_performance` for held ISINs (§4.5).

History backfill (first run, and for any newly held ISIN): AMFI's history report endpoint (`portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?mf=<amc>&frmdt=…&todt=…`) per AMC in 3-month windows, for held schemes and their category peers. It is slow and occasionally flaky; it runs as a background job with progress shown in the UI. `mfapi.in` (unofficial mirror) is an optional alternative provider behind the same `NavProvider` interface.

### 4.3 XIRR `[CODE]`

Cashflows for a scheme (or folio, or portfolio) as of date `T` (latest NAV date):

- every `mf_transaction` with non-null `amount`: purchases, SIPs, switch-ins → negative; redemptions, switch-outs, IDCW payouts → positive; IDCW reinvest and stamp duty/STT → 0 (already reflected in units/amounts);
- terminal flow `+ units × NAV(T)` at `T`.

Solve `Σ cf_i / (1+r)^((d_i − d_0)/365) = 0` with Newton–Raphson from `r=0.1`, 100 iterations, tolerance `1e-7`; on non-convergence or leaving `(-0.9999, 10)`, bisection on `[-0.99, 10]`. Undefined (null) when there is no positive and no negative flow, or when the earliest flow is < 30 days before `T` (annualizing a few weeks is meaningless; absolute return is shown instead). Portfolio XIRR uses the union of all flows. Unit-tested against Excel `XIRR` fixtures.

### 4.4 Allocation

`GET /portfolio/summary?group_by=category|amc|member|folio`. Category uses `scheme_master.category_bucket`: `LARGE_CAP`, `LARGE_MID_CAP`, `MID_CAP`, `SMALL_CAP`, `FLEXI_MULTI_CAP`, `OTHER_EQUITY` (sectoral, thematic, ELSS, focused, value, contra, dividend yield), `DEBT`, `HYBRID`, `INDEX_ETF`, `SOLUTION`, `OTHER`. Weight = `value / Σ value`. Each group also reports invested cost, current value, absolute gain, and group XIRR.

### 4.5 "Underperformed" — precise definition

For scheme `s` on as-of date `T` with benchmark `B`:

```
NAV(s, x)     = nav from nav_history with the latest nav_date ≤ x, provided nav_date ≥ x − 7 days; else undefined
ret_1y(s, T)  = NAV(s, T) / NAV(s, T − 1 year) − 1
cagr_3y(s, T) = (NAV(s, T) / NAV(s, T − 3 years))^(1/3) − 1
same formulas for B over benchmark_history (basis INDEX) or over the peer set (basis PEER_MEDIAN, below)

underperformed(s, T) =
  NOT_APPLICABLE     if category_bucket = INDEX_ETF   (tracking difference is reported instead)
  INSUFFICIENT_DATA  if any of ret_1y(s), cagr_3y(s), ret_1y(B), cagr_3y(B) is undefined
  YES                if ret_1y(s) < ret_1y(B) − 0.01  AND  cagr_3y(s) < cagr_3y(B) − 0.01
  NO                 otherwise
```

Both horizons must lag by more than 1 percentage point. A fund that is only behind over one horizon is not flagged. A fund younger than 3 years is `INSUFFICIENT_DATA`, not `NO`. The 1-pp tolerance and the AND rule are chosen to avoid flagging on noise; they are constants in `PerformanceService` and appear in the API response so the UI can show the rule.

Benchmark `B` resolution:

1. `scheme_master.benchmark_index` (seeded from AMFI category via a static map, e.g. Large Cap → `NIFTY 100 TRI`, Mid Cap → `NIFTY Midcap 150 TRI`, Small Cap → `NIFTY Smallcap 250 TRI`, Flexi/Multi/ELSS/Focused/Value → `NIFTY 500 TRI`, Aggressive Hybrid → `CRISIL Hybrid 35+65 Aggressive`, debt categories → the matching CRISIL index; user-overridable per scheme) **if** `benchmark_history` has data for it at both `T` and `T − 3y` → `basis = INDEX`.
2. Otherwise **peer median**: all schemes in `scheme_master` with the same AMFI `category` and the same `plan` (Direct vs Regular) and `option_type = GROWTH`, with defined `ret_1y` and `cagr_3y`; `ret_1y(B)` = median of peers' `ret_1y`, likewise for `cagr_3y`; requires `peer_count ≥ 5`, else `INSUFFICIENT_DATA` → `basis = PEER_MEDIAN`.

Plain statement of the limitation: there is no free, stable API for NSE/BSE/CRISIL total-return index history. v1 ships with `benchmark_history` empty and a CSV import endpoint (`POST /portfolio/benchmarks/import`, format `index_name,date,value`, e.g. exported from niftyindices.com). Until you import, every flag is computed on `PEER_MEDIAN` and labelled as such. See open question 2.

Results are stored in `scheme_performance` per (isin, as_of_date) so the dashboard is a plain read and the history of flags is queryable.

---

## 5. Chatbot

### 5.1 Runtime

- OpenAI Responses API via `openai-java`; `store: false`; history is kept in `chat_message` and replayed as `input` items each turn (rejected `previous_response_id`: it would keep conversation state on OpenAI's side).
- Server-side tool loop: max 6 tool rounds per user message, 60 s wall clock, then the model is asked to answer with what it has.
- Model name is `docshelf.openai.chat-model` (config). Reasoning/temperature defaults are conservative; `parallel_tool_calls: true`.
- v1 responds synchronously (one JSON per user message, containing the assistant message, tool-call summaries, and an optional `pendingAction`). Streaming is a later enhancement (§10).

### 5.2 System prompt content (what the model is told)

- Role and rules: answer only from tool results; never guess numbers; sensitive fields are masked and can only be revealed via a confirmation the **user presses in the UI**; if the user types "yes"/"confirm", reply that the Confirm button must be pressed; never ask for or repeat full ID numbers.
- Roster (names only, no numbers): family members `{id, name, relation, has: [AADHAAR, PASSPORT, …]}`, contacts `{id, name, relation, channels: [EMAIL, WHATSAPP]}`. This is the only PII that goes in every prompt; it is what makes "my mother" and "Ramesh" resolvable without a lookup tool.
- Today's date, timezone, currency formatting.

### 5.3 Tools (JSON schema, abbreviated)

```json
[
 {"name":"search_documents","description":"Hybrid semantic + keyword search over redacted document text. Returns snippets and document ids.",
  "parameters":{"type":"object","properties":{
    "query":{"type":"string"},
    "member_id":{"type":["string","null"]},
    "doc_type":{"type":["string","null"],"enum":["INSURANCE_POLICY","MF_CAS","AADHAAR","PASSPORT","DRIVING_LICENCE","PAN_CARD","OTHER",null]},
    "limit":{"type":"integer","default":6}},"required":["query"]}},

 {"name":"get_field","description":"Read an extracted field. Sensitive fields come back masked; pass reveal=true to request a full reveal, which requires user confirmation.",
  "parameters":{"type":"object","properties":{
    "document_id":{"type":"string"},
    "field":{"type":"string"},
    "reveal":{"type":"boolean","default":false}},"required":["document_id","field"]}},

 {"name":"portfolio_summary","description":"Portfolio totals, XIRR and allocation.",
  "parameters":{"type":"object","properties":{
    "group_by":{"type":"string","enum":["category","amc","member","folio","scheme"]},
    "member_id":{"type":["string","null"]}},"required":["group_by"]}},

 {"name":"list_reminders","description":"Upcoming reminders.",
  "parameters":{"type":"object","properties":{
    "days_ahead":{"type":"integer","default":90},
    "member_id":{"type":["string","null"]}}}},

 {"name":"share_document","description":"Request to send a document to a contact. Never sends directly: returns a confirmation request the user must approve in the UI.",
  "parameters":{"type":"object","properties":{
    "document_id":{"type":"string"},
    "contact_id":{"type":"string"},
    "channel":{"type":"string","enum":["EMAIL","WHATSAPP"]}},"required":["document_id","contact_id","channel"]}}
]
```

Tool results are JSON built by Java; every string that reaches the model passes through the same masking used by the REST API (ID numbers masked, no addresses).

### 5.4 Confirmation protocol (applies to `share_document` and `get_field(reveal=true)`)

1. Tool handler validates the request (document exists, contact has the channel, field is sensitive) and inserts a `pending_action` (`PENDING`, expires in 10 minutes), audit `PENDING_ACTION_CREATED`.
2. Tool result to the model: `{"status":"CONFIRMATION_REQUIRED","pending_action_id":"…","summary":"…masked…"}`. The model has no tool to confirm.
3. The API response to the UI carries `pendingAction` **from the database row**, not from model text. The UI renders a card with Confirm / Cancel.
4. Confirm → `POST /pending-actions/{id}/confirm` → server re-checks status and expiry → executes via the same `ShareService` / `RevealService` the UI uses directly → audit → result. For `REVEAL_FIELD`, the revealed value is returned **to the UI only**; a `DEVELOPER` message "Pending action … executed (value shown to user, not available to you)" is appended to the chat so the model knows the outcome without ever seeing the number. For `SHARE_DOCUMENT`, the developer note contains the delivery status and provider message id.
5. Cancel / expiry → `CANCELLED` / `EXPIRED`, developer note appended, the model can offer alternatives.

### 5.5 Full flow: "send my mother's Aadhaar to Ramesh on WhatsApp"

```mermaid
sequenceDiagram
  autonumber
  participant U as User (UI)
  participant API as ChatController
  participant SVC as ChatService (tool loop)
  participant OAI as OpenAI Responses API
  participant T as Tool handlers
  participant DB as PostgreSQL
  participant WA as WhatsAppGateway (stub in v1)

  U->>API: POST /chat/sessions/{s}/messages {"content":"send my mother's Aadhaar to Ramesh on WhatsApp"}
  API->>DB: insert chat_message(USER)
  SVC->>OAI: input = [system(roster: mother=fm_2 "Sunita", Ramesh=c_5 [WHATSAPP]), history, user], tools
  OAI-->>SVC: function_call search_documents{query:"Aadhaar", member_id:"fm_2", doc_type:"AADHAAR"}
  SVC->>T: search_documents
  T->>DB: hybrid search, filter member=fm_2
  T-->>SVC: [{document_id:"d_17", type:"AADHAAR", title:"Aadhaar — Sunita", number_masked:"XXXX XXXX 1234", status:"READY"}]
  SVC->>DB: insert chat_message(TOOL)
  SVC->>OAI: function_call_output
  OAI-->>SVC: function_call share_document{document_id:"d_17", contact_id:"c_5", channel:"WHATSAPP"}
  SVC->>T: share_document
  T->>DB: validate doc READY, contact c_5 has whatsapp_e164 & opted in
  T->>DB: insert pending_action(SHARE_DOCUMENT, PENDING, expires +10m); audit PENDING_ACTION_CREATED
  T-->>SVC: {status:"CONFIRMATION_REQUIRED", pending_action_id:"pa_91", summary:"Aadhaar — Sunita (XXXX XXXX 1234) → Ramesh Patel via WhatsApp (+91 98•••• •210)"}
  SVC->>OAI: function_call_output
  OAI-->>SVC: text: "Ready to send Sunita's Aadhaar (XXXX XXXX 1234) to Ramesh on WhatsApp. Please press Confirm."
  SVC->>DB: insert chat_message(ASSISTANT)
  API-->>U: 200 {message, toolCalls:[search_documents, share_document], pendingAction:{id:"pa_91", type:"SHARE_DOCUMENT", summary, expiresAt}}
  Note over U: UI renders confirmation card from pendingAction (not from model text)

  U->>API: POST /pending-actions/pa_91/confirm
  API->>DB: SELECT ... FOR UPDATE; status=PENDING and not expired?
  API->>DB: decrypt DEK, read blob
  API->>WA: uploadMedia(pdf) → mediaId ; sendTemplate(to, "docshelf_share_document", header=doc mediaId)
  WA-->>API: {providerMessageId | SIMULATED}
  API->>DB: outbound_message(SENT|SIMULATED); pending_action EXECUTED; audit SHARE_DOCUMENT(origin=CHAT, doc d_17, contact c_5, channel WHATSAPP)
  API->>DB: insert chat_message(DEVELOPER, "pa_91 executed: WhatsApp message wamid.… accepted")
  API-->>U: 200 {status:"EXECUTED", result:{channel:"WHATSAPP", providerMessageId:"…", simulated:true}}
  Note over U: UI shows "Sent ✓ (simulated)" under the card

  U->>API: POST /chat/sessions/{s}/messages {"content":"thanks, did it go?"}
  SVC->>OAI: history now includes the DEVELOPER note
  OAI-->>SVC: "Yes — it was accepted by WhatsApp at 14:02."
```

If the user had typed "yes" at step 17 instead of pressing Confirm, the model has no tool to act on it and replies per its instructions that the button must be pressed. The pending action stays valid until expiry.

If the search had returned two Aadhaar documents for Sunita (front/back scans), the model is instructed to ask which one, and the UI shows both titles.

---

## 6. Sharing

Both the chat path (after confirmation) and the UI path (Share dialog, executes immediately because the user clicked) call one `ShareService.execute(documentId, contactId, channel, origin)`, which: decrypts the blob to memory, builds the attachment, writes `outbound_message` (unique `dedupe_key = share|documentId|contactId|channel|minuteBucket`), calls the channel gateway, records the audit row, and never stores a decrypted copy on disk.

### 6.1 Email (real in v1)

- Spring `JavaMailSender` over SMTP. Dev: Mailpit in Compose. Prod: Gmail SMTP with an App Password (`smtp.gmail.com:587`, STARTTLS) or any SMTP relay. Gmail API was rejected for v1: it needs a Google Cloud project, OAuth consent screen, refresh-token storage, and offers nothing SMTP does not for a single sender.
- Message: subject `DocShelf: <title> for <member>`, short body with who shared it, attachment = the working PDF (password-removed copy if the original was locked).
- Option (off by default, per-share toggle): re-protect the attachment with a passphrase using PDFBox `StandardProtectionPolicy` (AES-256) and show the passphrase to you to relay separately. See open question 7.
- `Message-ID` set from `dedupe_key`; Mailpit shows it, real SMTP servers keep it.

### 6.2 WhatsApp — Meta WhatsApp Business Cloud API (interface + stub in v1)

Real implementation shape (`MetaCloudWhatsAppGateway`), Graph API version pinned in config:

```
POST https://graph.facebook.com/{version}/{PHONE_NUMBER_ID}/media
  multipart: messaging_product=whatsapp, type=application/pdf, file=<bytes>      → {"id":"<media_id>"}

POST https://graph.facebook.com/{version}/{PHONE_NUMBER_ID}/messages
{
  "messaging_product": "whatsapp",
  "to": "919876543210",
  "type": "template",
  "template": {
    "name": "docshelf_share_document",
    "language": {"code": "en"},
    "components": [
      {"type": "header", "parameters": [{"type": "document", "document": {"id": "<media_id>", "filename": "Aadhaar-Sunita.pdf"}}]},
      {"type": "body",   "parameters": [{"type": "text", "text": "Sarthak"}, {"type": "text", "text": "Aadhaar (Sunita)"}]}
    ]
  }
}                                                                                → {"messages":[{"id":"wamid.…"}]}
```

Constraints of that API, stated plainly:

1. **You cannot send free-form messages to someone who has not messaged you in the last 24 hours.** Business-initiated messages must be pre-approved **template messages**. Sharing a document means a template with a document header; the body text is fixed at template approval time, only the placeholders vary.
2. **Template approval** by Meta takes minutes to days and can be rejected. The template category (Utility vs Marketing) affects price and approval.
3. **Setup prerequisites**: a Meta Business account, a WhatsApp Business Account, business verification for anything beyond the test tier, and a phone number that is **not** registered on the consumer WhatsApp app (it gets migrated and stops working in the app). A permanent System User access token must be stored as a secret.
4. **Test tier**: the free test number can message only up to 5 pre-registered recipient numbers, which is enough for personal use but each recipient must be added in the Meta dashboard first.
5. **Opt-in**: Meta's policy requires the recipient to have opted in to receive messages from your number. `contact.whatsapp_opted_in` exists to record that you obtained it; the app refuses to send when it is false.
6. **Media**: upload first, then reference by id. Documents up to 100 MB, PDF allowed. Uploaded media ids expire after 30 days (irrelevant here, we send immediately). Media is stored on Meta's servers until then.
7. **Delivery status is only available through webhooks**, which need a public HTTPS endpoint. Without it, the app knows "accepted by the API" (`wamid`), not "delivered" or "read". v1 records `SENT` on acceptance and labels it as such.
8. **No idempotency key** on the messages endpoint; retries can duplicate. Hence the at-most-once handling in §3.3 / `outbound_message`.
9. **Pricing** is per 24-hour conversation, by template category and destination country (a small number of free conversations per month on the test tier is not guaranteed). Rate limits and a "quality rating" apply; blocked or reported messages reduce the allowed volume.
10. **Recipient side**: they see your business display name, and the document arrives as a normal WhatsApp file, i.e. unencrypted at rest on their phone and in their backups.

v1: `WhatsAppGateway` interface with `StubWhatsAppGateway` that validates inputs, writes `outbound_message` with `status='SIMULATED'` and `provider='STUB'`, logs the would-be request (without the PDF bytes), and returns a fake `wamid`. Switching to Meta is a config property plus secrets.

---

## 7. Security

### 7.1 Threat model (what this protects against)

Someone who obtains the Postgres data directory or the blob volume without the master key gets: encrypted PDFs, encrypted ID numbers, masked values, names, dates of birth, policy numbers, folios, amounts, and redacted chunk text. Someone with the master key and disk access gets everything; the master key is therefore the crown jewel and lives only in `.env`/environment. This is not a defence against a compromised running host.

### 7.2 Encryption at rest

- **KEK**: `DOCSHELF_MASTER_KEY` (32 random bytes, base64) → HKDF-SHA256 into two sub-keys: `kek_blob` and `kek_field`, plus `hmac_key`. `kek_id` (`"env-v1"`) is stored with every ciphertext so rotation is a re-wrap job, not a re-encrypt.
- **Blobs**: per-document random 256-bit DEK, AES-256-GCM, random 12-byte IV, AAD = document id. File layout: `DSH1 | kek_id | iv | ciphertext | tag`. DEK wrapped with AES-KW (RFC 3394) under `kek_blob`, stored in `document.dek_wrapped`. Decryption happens into memory for one request and the buffer is zeroed after use.
- **Fields**: AES-256-GCM under `kek_field`, random IV, AAD = `table.column:rowId`. Stored as `BYTEA` = `v1 | iv | ciphertext | tag`. Applied to: ID numbers, PAN, Aadhaar address, PDF passwords.
- **Masks** (plaintext, for display and search): Aadhaar `XXXX XXXX 1234`; PAN `XXXXX1234X`; passport `X•••••67` (first letter + last 2); driving licence `MH12 •••••••2345` (state code + last 4); phone `+91 98•••• •210`.
- **HMAC-SHA256** of the normalized number under `hmac_key` for duplicate detection and exact lookup without decrypting.
- Postgres itself is not encrypted (Docker volume on your disk). FileVault/disk encryption on the host is assumed. Backups must include `.env` or they are useless; this is documented in the README.

### 7.3 Reveal and audit

- Default API responses never contain a sensitive value, only the mask.
- `POST /documents/{id}/fields/{field}/reveal` returns the value once, with `Cache-Control: no-store`; the UI shows it for 30 s with a copy button, then re-masks. Every call writes `audit_log(REVEAL_FIELD, origin=UI|CHAT)`.
- Every share writes `audit_log(SHARE_DOCUMENT)` with contact label, channel, and provider message id.
- `GET /documents/{id}/file` (download/view original) writes `DOWNLOAD_ORIGINAL`.
- `audit_log` is append-only at the database level (trigger). The Audit tab in Document detail and a global Audit page read it.
- Application logs never include field values, chunk text, or prompt text; only ids, counts and timings.

### 7.4 What leaves the machine, and why

| Destination | When | What is sent | What is never sent |
|---|---|---|---|
| OpenAI (chat model) | classification fallback | redacted text of first 2 pages | ID numbers, PAN, phone numbers, MRZ lines |
| OpenAI (chat model) | insurance / Aadhaar / DL field extraction | redacted full text + regex hints | same |
| OpenAI (embeddings) | every chunk of every document | redacted chunk text | same |
| OpenAI (chat model) | each chat turn | system prompt (family and contact **names** and relations), chat history, masked tool results | full ID numbers, addresses, the PDFs |
| AMFI | nightly | a plain GET of a public file | anything about you |
| SMTP server / recipient | on confirmed share | the PDF and the recipient's address | — |
| Meta | on confirmed share | the PDF (stored on Meta servers up to 30 days), recipient number | — |

So, explicitly: **names, dates of birth, genders, addresses' city/state, policy numbers, sum assured and premium amounts, folio numbers, scheme names and holdings do go to OpenAI**, in redacted form. They are needed for extraction and for answering questions; redacting them would make the extractor useless. What is redacted is everything that is a government identifier or a payment/contact identifier. OpenAI's API terms state API inputs are not used for training and `store:false` prevents retention beyond the abuse-monitoring window; that is a contractual, not technical, control, and the document says so.

Redactor `[CODE]`, applied before every outbound OpenAI call, deterministic and unit-tested:

| Pattern | Rule | Placeholder |
|---|---|---|
| Aadhaar | 12 digits with optional spaces/hyphens, Verhoeff-valid, not preceded by "Folio" | `⟨AADHAAR#n⟩` |
| VID | 16 digits | `⟨VID#n⟩` |
| PAN | `[A-Z]{5}[0-9]{4}[A-Z]` | `⟨PAN#n⟩` |
| Passport | `[A-Z][0-9]{7}` near "Passport" / on MRZ | `⟨PASSPORT#n⟩` |
| MRZ | any line of 44 chars of `[A-Z0-9<]` | `⟨MRZ⟩` (whole line) |
| Driving licence | state pattern from §1.3 | `⟨DL#n⟩` |
| Phone | Indian mobile `(\+91|0)?[6-9]\d{9}` | `⟨PHONE#n⟩` |
| Email | RFC-ish | `⟨EMAIL#n⟩` |
| Bank account / long numbers | 9–18 consecutive digits not matched above and not a folio/policy context | `⟨NUM#n⟩` |

Counts per type are recorded in `llm_call.redaction_counts` so you can audit that redaction ran. The placeholder → value map lives only in memory for the duration of the call and is used solely to write the deterministic value into the typed field.

Alternative that removes OpenAI from the data path entirely: a local model via Ollama for extraction and a local ONNX embedding model. Rejected for v1 on extraction quality for insurance PDFs and on Compose complexity; the `LlmClient` and `EmbeddingClient` interfaces are the swap points. See open question 3.

### 7.5 Other

- Single hardcoded user, no auth: the backend binds to `127.0.0.1` in Compose; the frontend proxies. Do not expose port 8080 on a LAN without adding auth.
- Upload limits and PDF parsing run with PDFBox's memory-safe settings; malformed PDFs fail the job, not the process.
- Aadhaar handling follows the UIDAI display guidance (mask all but the last 4). Storing your own family's numbers for personal use is your data; the app is not a "requesting entity" under the Aadhaar Act.

---

## 8. API contract

Base path `/api/v1`. JSON, UTF-8, dates as ISO-8601 (`YYYY-MM-DD`), timestamps ISO with offset. Errors follow RFC 9457 Problem Details:

```json
{"type":"https://docshelf.local/errors/validation","title":"Validation failed","status":400,
 "detail":"fullName must not be blank","instance":"/api/v1/members",
 "errors":[{"field":"fullName","message":"must not be blank"}]}
```

Error codes used everywhere: `400` validation, `404` not found, `409` conflict (duplicate upload, illegal state transition), `410` pending action expired, `413` file too large, `415` unsupported media type, `422` semantically invalid (wrong PDF password, unparsable CSV), `502` upstream failure (OpenAI, AMFI, SMTP, Meta), `503` ingestion worker unavailable. Only additional codes are listed per endpoint.

"Idem" column: ✓ = safe to retry with identical effect.

### 8.1 Family members and contacts

| Method | Path | Idem | Notes |
|---|---|---|---|
| GET | `/members` | ✓ | list |
| POST | `/members` | ✗ | create |
| GET | `/members/{id}` | ✓ | |
| PUT | `/members/{id}` | ✓ | full replace; changing `dob` re-materializes birthday reminders |
| DELETE | `/members/{id}` | ✓ | `409` if documents still reference the member (unassign first) |
| GET | `/contacts` | ✓ | |
| POST | `/contacts` | ✗ | |
| GET | `/contacts/{id}` | ✓ | |
| PUT | `/contacts/{id}` | ✓ | |
| DELETE | `/contacts/{id}` | ✓ | past `outbound_message` rows keep `contact_label` |

```json
// FamilyMember (request omits id/timestamps; pan is write-only)
{"id":"…","fullName":"Sunita Brahmbhatt","relation":"MOTHER","isSelf":false,"dob":"1958-04-12","gender":"F",
 "panMasked":"XXXXX1234X","email":null,"phoneE164":null,"notes":null,"documentCount":3,
 "createdAt":"…","updatedAt":"…"}
// request-only field:  "pan":"ABCDE1234F"

// Contact
{"id":"…","fullName":"Ramesh Patel","relation":"Uncle","email":"ramesh@example.com",
 "whatsappE164":"+919876543210","whatsappOptedIn":true,"notes":null,"createdAt":"…","updatedAt":"…"}
```

### 8.2 Documents

| Method | Path | Idem | Notes |
|---|---|---|---|
| POST | `/documents` | ✓ (by sha256) | multipart `file`, optional `memberId`, `typeHint`, `title`. `202` with the new document; `409` with the existing document if the same bytes were uploaded before |
| GET | `/documents` | ✓ | query `type`, `memberId`, `status`, `q` (title search), `page`, `size` |
| GET | `/documents/{id}` | ✓ | includes typed `fields` (masked) |
| PATCH | `/documents/{id}` | ✓ | `title`, `memberId`, `docType` (setting type re-runs extraction, `classifier_source=USER`) |
| DELETE | `/documents/{id}` | ✓ | removes blob, chunks, fields, pending reminders; audit `DELETE_DOCUMENT` |
| GET | `/documents/{id}/file` | ✓ | streams decrypted PDF (`application/pdf`, `Content-Disposition: inline`); audit `DOWNLOAD_ORIGINAL` |
| POST | `/documents/{id}/password` | ✓ | `{"password":"…"}`; `422` if it does not open the PDF; re-queues job |
| POST | `/documents/{id}/reprocess` | ✓ | `{"from":"TEXT"|"FIELDS"|"INDEX"}`; `409` if a job is already running |
| GET | `/documents/{id}/fields` | ✓ | typed fields with per-field meta |
| PUT | `/documents/{id}/fields` | ✓ | manual correction; body is the typed field object; audit `FIELD_EDIT`; re-materializes reminders |
| POST | `/documents/{id}/fields/{field}/reveal` | ✗ (audited each time) | `{"purpose":"…optional"}` → full value; `400` if field is not sensitive |
| GET | `/documents/{id}/audit` | ✓ | audit rows for this document |

```json
// Document
{"id":"d_17","docType":"AADHAAR","status":"READY","title":"Aadhaar — Sunita",
 "member":{"id":"fm_2","fullName":"Sunita Brahmbhatt","relation":"MOTHER"},
 "originalFilename":"eaadhaar.pdf","mimeType":"application/pdf","sizeBytes":234123,"pageCount":2,"ocrUsed":false,
 "classifier":{"source":"RULE","confidence":0.97},
 "extraction":{"source":"MIXED","confidence":0.91,"needsReviewReasons":[]},
 "uploadedAt":"…","processedAt":"…","lastError":null,
 "fields":{"idType":"AADHAAR","numberMasked":"XXXX XXXX 1234","nameOnDocument":"Sunita Brahmbhatt",
           "dob":"1958-04-12","yearOfBirth":null,"gender":"F","issueDate":null,"expiryDate":null,
           "issuingAuthority":"UIDAI","addressMasked":"Ahmedabad, Gujarat",
           "meta":{"number":{"source":"RULE","confidence":1.0,"page":1},"nameOnDocument":{"source":"LLM","confidence":0.88,"page":1}}}}

// fields for INSURANCE_POLICY
{"insurer":"Star Health","policyNo":"P/123456/01/2026/001234","policyType":"HEALTH","planName":"Family Health Optima",
 "sumAssured":1000000.00,"premiumAmount":24500.00,"premiumFrequency":"YEARLY","premiumDueDate":"2027-03-14",
 "policyStart":"2026-03-15","policyExpiry":"2027-03-14","nomineeName":"…","nomineeRelation":"SPOUSE",
 "insuredMembers":[{"memberId":"fm_1","name":"Sarthak"},{"memberId":null,"name":"…"}],"meta":{…}}

// fields for MF_CAS
{"statementFrom":"2025-04-01","statementTo":"2026-03-31","asOfDate":"2026-03-31","investorPanMasked":"XXXXX1234X",
 "folios":[{"folioId":"…","folioNo":"1234567/89","amc":"…","schemes":[{"isin":"INF…","schemeName":"…","closingUnits":123.456,"nav":45.6789,"navDate":"2026-03-31","value":5637.66,"transactionCount":24}]}],
 "reconciliation":{"ok":true,"mismatches":[]}}

// reveal response
{"field":"number","value":"1234 5678 9012","auditId":8812,"displaySeconds":30}
```

### 8.3 Reminders

| Method | Path | Idem | Notes |
|---|---|---|---|
| GET | `/reminders` | ✓ | query `from`, `to` (fire_date range), `status[]`, `memberId`, `documentId` |
| GET | `/reminders/{id}` | ✓ | |
| POST | `/reminders/{id}/dismiss` | ✓ | `PENDING|SNOOZED|SENT → DISMISSED`; `409` otherwise |
| POST | `/reminders/{id}/snooze` | ✓ | `{"until":"2026-10-01"}`; must be before `eventDate` |
| POST | `/reminders/{id}/resend` | ✗ | only from `FAILED|UNKNOWN`; audited |
| GET | `/reminder-rules` | ✓ | |
| PUT | `/reminder-rules/{ruleKey}` | ✓ | `{"enabled":true,"offsetsDays":[30,7,1],"channels":["IN_APP","EMAIL"]}`; re-materializes |
| POST | `/reminders/run` | ✓ | `{"phase":"MATERIALIZE"|"DISPATCH"|"BOTH"}` → counts; `409` if already running |

```json
{"id":"…","ruleKey":"POLICY_EXPIRY","sourceType":"DOCUMENT","sourceId":"d_3","documentTitle":"Star Health — Sarthak",
 "member":{"id":"fm_1","fullName":"Sarthak"},"eventDate":"2027-03-14","offsetDays":30,"fireDate":"2027-02-12",
 "channel":"EMAIL","status":"PENDING","title":"Star Health policy expires in 30 days","body":"…",
 "sentAt":null,"error":null}
```

### 8.4 Portfolio

| Method | Path | Idem | Notes |
|---|---|---|---|
| GET | `/portfolio/summary` | ✓ | `group_by=category|amc|member|folio|scheme`, `memberId?` |
| GET | `/portfolio/holdings` | ✓ | flat list with performance flag; `memberId?`, `underperformedOnly?` |
| GET | `/portfolio/holdings/{isin}` | ✓ | `folioId?`; scheme detail, transactions, NAV series (`from`,`to`), performance |
| GET | `/portfolio/transactions` | ✓ | `isin?`, `folioId?`, `from?`, `to?`, paging |
| GET | `/portfolio/performance` | ✓ | latest `scheme_performance` per held ISIN with the rule constants |
| POST | `/portfolio/nav/refresh` | ✓ | triggers AMFI fetch; `202`; `409` if running; `502` on AMFI failure |
| POST | `/portfolio/nav/backfill` | ✓ | `{"isins":[…]}` or all held; `202` |
| GET | `/portfolio/jobs` | ✓ | status of refresh/backfill jobs |
| POST | `/portfolio/benchmarks/import` | ✓ (by row PK) | multipart CSV `index_name,date,value`; `422` on bad rows with line numbers |
| GET | `/schemes/{isin}` | ✓ | scheme master |
| PUT | `/schemes/{isin}` | ✓ | override `categoryBucket`, `benchmarkIndex` (`source=USER`) |

```json
// summary
{"asOfDate":"2026-09-05","invested":1250000.00,"currentValue":1612340.55,"absoluteGain":362340.55,"absoluteReturnPct":28.99,
 "xirr":0.1412,"xirrDefined":true,
 "groups":[{"key":"MID_CAP","label":"Mid Cap","invested":…,"currentValue":…,"weightPct":31.2,"xirr":0.171,"schemeCount":2}],
 "rule":{"underperformToleranceP":0.01,"horizons":["1Y","3Y"],"logic":"AND"}}

// holding row
{"folioNo":"1234567/89","member":{"id":"fm_1","fullName":"Sarthak"},"isin":"INF…","schemeName":"…","amc":"…",
 "categoryBucket":"MID_CAP","units":123.456,"nav":45.6789,"navDate":"2026-09-05","value":5637.66,"costValue":4200.00,
 "xirr":0.1712,"performance":{"asOfDate":"2026-09-05","ret1y":0.182,"cagr3y":0.211,"benchRet1y":0.201,"benchCagr3y":0.224,
   "benchmarkName":"Equity Scheme - Mid Cap Fund (peer median, n=27)","basis":"PEER_MEDIAN","underperformed":"YES"}}
```

### 8.5 Chat and pending actions

| Method | Path | Idem | Notes |
|---|---|---|---|
| POST | `/chat/sessions` | ✗ | `{"title?":"…"}` |
| GET | `/chat/sessions` | ✓ | |
| GET | `/chat/sessions/{id}/messages` | ✓ | roles USER/ASSISTANT only by default; `includeTool=true` for tool rows (masked) |
| POST | `/chat/sessions/{id}/messages` | ✗ (optional `Idempotency-Key` header, 24 h) | `{"content":"…"}` → runs the tool loop; `502` on OpenAI failure with the user message still stored |
| DELETE | `/chat/sessions/{id}` | ✓ | |
| GET | `/pending-actions/{id}` | ✓ | |
| POST | `/pending-actions/{id}/confirm` | ✓ | second call returns the same `EXECUTED` result; `410` expired; `409` cancelled/failed |
| POST | `/pending-actions/{id}/cancel` | ✓ | |

```json
// POST messages response
{"message":{"id":"…","role":"ASSISTANT","content":"Ready to send …","createdAt":"…"},
 "toolCalls":[{"name":"search_documents","argumentsSummary":"Aadhaar · Sunita"},{"name":"share_document","argumentsSummary":"d_17 → Ramesh · WHATSAPP"}],
 "pendingAction":{"id":"pa_91","type":"SHARE_DOCUMENT","summary":"Aadhaar — Sunita (XXXX XXXX 1234) → Ramesh Patel via WhatsApp (+91 98•••• •210)",
                  "expiresAt":"…","status":"PENDING"},
 "usage":{"inputTokens":2310,"outputTokens":142}}

// confirm response (SHARE_DOCUMENT)
{"id":"pa_91","status":"EXECUTED","result":{"channel":"WHATSAPP","providerMessageId":"wamid.…","simulated":true,"outboundMessageId":"…"}}
// confirm response (REVEAL_FIELD)
{"id":"pa_92","status":"EXECUTED","result":{"field":"number","value":"1234 5678 9012","auditId":8813,"displaySeconds":30}}
```

### 8.6 Shares, audit, system

| Method | Path | Idem | Notes |
|---|---|---|---|
| POST | `/shares` | ✗ | UI-initiated share `{"documentId","contactId","channel","protectAttachment":false}` → executes now; `409` if contact lacks channel or not opted in |
| GET | `/shares` | ✓ | outbound messages of type share, with status |
| GET | `/audit` | ✓ | `from`, `to`, `action[]`, `documentId`, paging |
| GET | `/system/health` | ✓ | DB, blob volume, OpenAI key present, SMTP reachable, last NAV refresh |
| GET | `/system/llm-usage` | ✓ | tokens by purpose and day |

---

## 9. UI

Angular 18+ standalone components, Angular Material only, reactive forms, `provideHttpClient` with a typed `ApiService` (one method per endpoint, DTO interfaces generated by hand from §8), an `ErrorInterceptor` mapping Problem Details to a snackbar, and a `LoadingState<T>` union (`idle | loading | error | ready`) used by every page. Routes: `/shelf`, `/documents/:id`, `/family`, `/reminders`, `/portfolio`, `/chat`, `/audit`.

Shared: `AppShellComponent` (toolbar, sidenav, upload FAB, pending-review badge from `GET /documents?status=NEEDS_REVIEW`), `MaskedValueComponent` (mask + reveal button + 30 s countdown), `ConfirmDialogComponent`, `StatusChipComponent`, `EmptyStateComponent`, `ErrorBannerComponent`, `SkeletonComponent`.

### 9.1 Shelf (`/shelf`)

```
ShelfPageComponent                         GET /documents?type&memberId&status&q&page
├── ShelfFilterBarComponent                GET /members (member filter)
├── UploadDropzoneComponent                POST /documents  (multipart, per-file progress)
│   └── UploadQueueComponent               GET /documents/{id} (poll 2 s until READY/NEEDS_REVIEW/FAILED/NEEDS_PASSWORD)
│       └── PasswordPromptDialogComponent  POST /documents/{id}/password
├── DocumentGridComponent
│   └── DocumentCardComponent  (type icon, owner chip, status chip, key date, "needs review" badge)
└── MatPaginator
```

### 9.2 Document detail (`/documents/:id`)

```
DocumentDetailPageComponent                GET /documents/{id}
├── DocumentHeaderComponent                PATCH /documents/{id} (title, owner, type override)  ·  DELETE /documents/{id}
│   ├── ShareButton → ShareDialogComponent GET /contacts · POST /shares
│   └── ReprocessMenu                      POST /documents/{id}/reprocess
├── MatTabGroup
│   ├── FieldsTab
│   │   ├── InsuranceFieldsComponent       (form)          PUT /documents/{id}/fields
│   │   ├── IdDocumentFieldsComponent      (form + MaskedValueComponent)  POST /documents/{id}/fields/{field}/reveal
│   │   ├── CasFieldsComponent             (folio/scheme table, reconciliation banner; links to /portfolio)
│   │   └── FieldConfidenceHintComponent   (per-field source/confidence from `meta`)
│   ├── OriginalTab
│   │   └── PdfViewerComponent             GET /documents/{id}/file → blob URL in <iframe> (no extra library)
│   ├── RemindersTab
│   │   └── ReminderListComponent          GET /reminders?documentId
│   └── AuditTab
│       └── AuditTableComponent            GET /documents/{id}/audit
```

### 9.3 Family members (`/family`) — contacts live here as a second tab (you did not list a Contacts screen)

```
FamilyPageComponent
└── MatTabGroup
    ├── MembersTab
    │   ├── MemberListComponent            GET /members
    │   │   └── MemberCardComponent        (name, relation, DOB, doc counts by type → links to /shelf?memberId)
    │   └── MemberFormDialogComponent      POST /members · PUT /members/{id} · DELETE /members/{id}  (PAN write-only field)
    └── ContactsTab
        ├── ContactListComponent           GET /contacts
        └── ContactFormDialogComponent     POST /contacts · PUT /contacts/{id} · DELETE /contacts/{id} (opt-in checkbox)
```

### 9.4 Reminders calendar (`/reminders`)

```
RemindersPageComponent
├── RemindersToolbarComponent              (month nav, status filter, "Run now" → POST /reminders/run, rules gear)
│   └── ReminderRulesDialogComponent       GET /reminder-rules · PUT /reminder-rules/{key}
├── CalendarMonthComponent                 GET /reminders?from&to      (hand-built 7×6 grid with Material cards; no calendar lib)
│   └── CalendarDayCellComponent
│       └── ReminderChipComponent → ReminderDetailSheetComponent (MatBottomSheet)
│                                          POST /reminders/{id}/dismiss · /snooze · /resend
└── UpcomingListComponent                  GET /reminders?from=today&to=+90d&status=PENDING,SNOOZED
```

### 9.5 Portfolio dashboard (`/portfolio`)

```
PortfolioPageComponent                     GET /portfolio/summary?group_by=category
├── PortfolioToolbarComponent              (member filter → GET /members; "Refresh NAV" → POST /portfolio/nav/refresh; job status → GET /portfolio/jobs)
├── SummaryTilesComponent                  (invested, value, gain, XIRR, as-of date)
├── AllocationChartComponent               GET /portfolio/summary?group_by=…   (inline SVG donut + legend, hand-written; see open question 9)
├── HoldingsTableComponent (MatTable)      GET /portfolio/holdings   (sort, "underperformed only" toggle, basis tooltip)
│   └── HoldingDetailDrawerComponent       GET /portfolio/holdings/{isin}?folioId
│       ├── NavSparklineComponent          (inline SVG line)
│       ├── TransactionsTableComponent     GET /portfolio/transactions?isin&folioId
│       └── SchemeOverrideDialogComponent  PUT /schemes/{isin}
└── BenchmarkImportDialogComponent         POST /portfolio/benchmarks/import
```

### 9.6 Chat (`/chat`)

```
ChatPageComponent
├── SessionListComponent                   GET /chat/sessions · POST /chat/sessions · DELETE /chat/sessions/{id}
└── ChatThreadComponent                    GET /chat/sessions/{id}/messages
    ├── MessageBubbleComponent
    │   └── ToolCallChipsComponent         (search_documents · share_document …, expandable)
    ├── PendingActionCardComponent         GET /pending-actions/{id} · POST …/confirm · POST …/cancel  (countdown to expiry)
    │   └── RevealedValueCardComponent     (shows revealed value 30 s, then masks; never persisted client-side)
    └── ComposerComponent                  POST /chat/sessions/{id}/messages  (disabled while a turn is running; shows tool activity)
```

### 9.7 Audit (`/audit`) — not in your list, added because §7 needs a place to read the log

```
AuditPageComponent
└── AuditTableComponent                    GET /audit?from&to&action
```

---

## 10. Trade-offs

| Decision | Chosen | Rejected alternative | Why |
|---|---|---|---|
| **Extract at upload vs RAG-only** | Extract typed fields at upload **and** index chunks for RAG | RAG-only (ask the LLM each time) | Reminders, XIRR, allocation, masking and dedupe all need typed, validated, deterministic values; RAG-only would re-send PII on every query, be non-reproducible, and make "when does my policy expire" a probabilistic answer. RAG stays for free-text questions the schema does not cover. Cost: an extraction schema per type and a review UI for low-confidence results |
| **CAS parsing: Java in-process vs Python `casparser` sidecar** | Java in-process, behind a `CasParser` interface | `casparser` (mature, handles CAMS/KFintech detailed+summary, passwords, many edge cases) in a small FastAPI sidecar | Sidecar adds a fourth container, a second language to maintain, a PAN-bearing PDF crossing a network hop, and version drift between casparser's output and our schema. The CAS text layout is regular enough for a state machine, and your sample PDFs become the unit-test corpus either way. **Risk I accept**: edge cases I have not seen (segregated portfolios, KFintech wrapped scheme names, multi-page folio spans). Mitigation: reconciliation check flags any parse that does not balance; the interface lets a sidecar replace the Java parser without touching callers. If more than ~2 of your sample files fail reconciliation in Phase 2, I will recommend flipping to the sidecar |
| **Which LLM calls can be code** | Classification: rules first, LLM only on low score. CAS: 100 % code. Passport: MRZ code. Aadhaar/PAN/DL numbers, DOBs, expiries: code. Insurance: LLM (formats vary too much). Names/addresses on ID docs: LLM on redacted text | LLM everything | Every deterministic step is testable, free, offline and never leaks. Expected LLM share on a typical upload: 0 calls for CAS and passports, 1 for Aadhaar/DL, 1–2 for insurance, plus embeddings |
| **Embeddings: OpenAI vs local ONNX** | OpenAI `text-embedding-3-small` on redacted chunks | Local `bge-small-en` / `multilingual-e5-small` via ONNX Runtime in the JVM (384 dims) | Simpler build, better recall. The local option keeps chunk text on the machine entirely and is a one-migration swap (`vector(384)`). This is the single largest volume of your text that leaves the machine, so I want your call: open question 3 |
| **Blob storage** | Encrypted files on a Docker volume | `BYTEA` in Postgres; MinIO/S3 | Files stream, backups stay small, no fourth container. Postgres `BYTEA` bloats dumps and WAL; MinIO is more than a single user needs |
| **Per-document DEK + wrapped key** | Yes | One key for all blobs | Rotation without re-encrypting every file; per-document AAD prevents blob swapping |
| **Unlocked working copy of password PDFs** | Store an unlocked copy (encrypted by us) alongside the original | Keep only the locked original + password | Re-processing and sharing do not need the PAN each time; our GCM encryption is stronger than PDF RC4/AES owner passwords anyway. Cost: 2× blob size for locked files |
| **Job queue** | `ingestion_job` table + single poller thread | Spring `@Async`; RabbitMQ/Redis | Survives restarts, visible in SQL, no broker. `@Async` loses jobs on crash |
| **Enums** | `TEXT` + `CHECK` | PG `ENUM` | Cheaper migrations |
| **Vector index** | HNSW cosine | IVFFlat | Tiny corpus; HNSW needs no training step |
| **Retrieval** | Hybrid: cosine + `ts_rank`, 0.6/0.4 after min-max normalization, top 8 | Vector only | Policy numbers, folio numbers, ISINs are exact tokens embeddings handle badly |
| **Chat confirmation** | UI button hitting a separate endpoint | Typed "yes" interpreted by the model + `confirm` tool | Your requirement that the model never executes shares/reveals. A typed "yes" would give the model the execution path; a button is out of the model's reach entirely. Cost: users must click |
| **Revealed values never enter model context** | Reveal result goes to UI only; model gets a developer note | Return the value as a tool result | Keeps full ID numbers out of every future prompt of that session |
| **Chat history** | Self-managed, `store:false` | `previous_response_id` | No conversation state retained by OpenAI; we already need the history for the UI |
| **Chat response** | Synchronous JSON | SSE streaming | Half the code; tool loops make streaming awkward. Streaming later needs only a new endpoint |
| **Email** | SMTP | Gmail API | See §6.1 |
| **OCR** | Tesseract in the container | Cloud OCR (Google Vision, Textract) | Scanned Aadhaar/passport images must not leave the machine unredacted, and the redactor needs text first. Cost: bigger image (~150 MB), lower accuracy on poor scans |
| **Benchmark data** | Static category→index map, CSV import, peer-median fallback | Scraping niftyindices.com; paid data | No stable free API; scraping breaks. Peer median is honest and computable from AMFI alone; the flag says which basis was used |
| **Underperformed rule** | Both 1y and 3y behind by > 1 pp | Either horizon; 0 pp tolerance | Fewer false positives from a single bad year; tolerance absorbs NAV-date mismatch noise |
| **Reminder semantics** | At-most-once for external channels, manual resend on `UNKNOWN` | At-least-once with retries | A duplicate WhatsApp/email is worse than a missed one that is visible in the UI |
| **PDF viewer** | `<iframe>` over a blob URL | `ng2-pdf-viewer` (pdf.js) | Your "Angular Material only" constraint; browser PDF rendering is adequate |
| **Charts** | Hand-written inline SVG | `ngx-charts` / Chart.js | Same constraint. A donut and a sparkline are ~150 lines of SVG. If you relax the constraint I would add Chart.js |
| **Soft vs hard delete** | Hard delete + audit row with label snapshots | Soft delete | Personal vault; you expect deleted means gone. Audit keeps the trace |

---

## 11. Open questions for you

1. **Sample PDFs.** Which insurers (and how many policies), CAMS or KFintech CAS, detailed or summary, e-Aadhaar PDFs or scans, passport as photo/scan? The extractor tests and the classifier rule weights are calibrated on these.
2. **Benchmark data.** Are you willing to import index TRI history as CSV from niftyindices.com when you want `INDEX` basis, or is `PEER_MEDIAN` (computed from AMFI data alone) acceptable as the permanent basis for the underperformed flag?
3. **Embeddings.** OpenAI embeddings on redacted chunks (simpler, better recall) or a local ONNX embedding model so chunk text never leaves the machine? Same question, weaker form, for extraction: is OpenAI receiving names, DOBs, policy numbers, amounts and holdings (redacted of government IDs) acceptable, per §7.4?
4. **OpenAI models.** Which chat model and reasoning setting should be the defaults? I will make both config properties; I need the values you have access to and want to pay for.
5. **Email sender.** Gmail SMTP with an App Password from your account, or another SMTP relay? Reminder emails go to which address?
6. **Reminder offsets and time.** I chose 7/1 days for premium due and 7/0 for birthdays; materialization at 06:00 IST, dispatch every 15 min. Confirm or change. Should WhatsApp reminders (to yourself) be wired at all in v1 given the stub?
7. **Attachment protection.** Should shared PDFs be re-encrypted with a passphrase by default (you relay the passphrase separately), or sent plain? Applies to email now and WhatsApp later.
8. **Aadhaar address.** Extract and store it (encrypted) or skip it entirely? Skipping reduces what goes to the LLM.
9. **Charts and PDF viewer.** Keep "Angular Material only" strictly (hand-written SVG donut/sparkline, iframe PDF viewer), or allow Chart.js and pdf.js?
10. **Family floater policies.** A health policy covers several members. Modelled as `insured_members` JSONB on the policy with optional member links. Do you want a proper link table (so a member's page lists floaters covering them) instead?
11. **PAN card as a document type.** I added `PAN_CARD` because PAN is needed as a CAS password and is itself an ID document. Keep, or store PAN only on the family member?
12. **CAS history.** When a newer CAS overlaps an older one, transactions are deduplicated and holdings replaced. Should older CAS documents be auto-archived, or kept on the shelf as-is?
13. **Deployment and backups.** Local Mac only? Where should the master key be backed up? I will document a `docshelf backup` script that dumps Postgres and tars the blob volume; it is only useful with the key.
14. **OCR languages.** `eng` only, or also `hin`/`guj` for Aadhaar back sides? Extra languages add ~30 MB each to the image.
15. **Sensitive-field scope in chat.** Reveal gating covers ID numbers, PAN and Aadhaar address. Policy numbers and folio numbers are shown masked in chat but revealable without the confirmation step (audited). Do you want them gated too?
16. **WhatsApp setup.** Do you already have a Meta Business account and a spare number? This determines whether the real gateway is worth wiring right after v1 or stays a stub for a while.
