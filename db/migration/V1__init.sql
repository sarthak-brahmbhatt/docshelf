-- DocShelf schema v0.2 (single init migration; nothing has shipped yet)
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
  pan_encrypted   BYTEA,
  pan_masked      TEXT,
  pan_hmac        CHAR(64),
  email           TEXT,
  phone_e164      TEXT,
  notes           TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_family_member_self ON family_member ((TRUE)) WHERE is_self;

CREATE TABLE contact (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name         TEXT NOT NULL,
  relation          TEXT,
  email             TEXT,
  whatsapp_e164     TEXT,
  whatsapp_opted_in BOOLEAN NOT NULL DEFAULT FALSE,
  notes             TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (email IS NOT NULL OR whatsapp_e164 IS NOT NULL)
);

CREATE TABLE user_settings (
  id               INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  reminder_email   TEXT,
  owner_whatsapp   TEXT,
  voice_language   TEXT NOT NULL DEFAULT 'en-IN',
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- documents ----------
CREATE TABLE document (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  member_id              UUID REFERENCES family_member(id) ON DELETE SET NULL,
  doc_type               TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (doc_type IN
                           ('INSURANCE_POLICY','MF_CAS','AADHAAR','PASSPORT','DRIVING_LICENCE','PAN_CARD',
                            'PRESCRIPTION','MEDICAL_REPORT','BILL_RECEIPT','VOICE_NOTE','NOTE','OTHER','UNKNOWN')),
  source                 TEXT NOT NULL DEFAULT 'UPLOAD' CHECK (source IN ('UPLOAD','CAMERA','VOICE','TEXT','CHAT')),
  title                  TEXT NOT NULL,
  status                 TEXT NOT NULL CHECK (status IN
                           ('UPLOADED','NEEDS_PASSWORD','EXTRACTING_TEXT','CLASSIFYING','EXTRACTING_FIELDS',
                            'INDEXING','READY','NEEDS_REVIEW','FAILED')),
  original_filename      TEXT NOT NULL,
  mime_type              TEXT NOT NULL,
  size_bytes             BIGINT NOT NULL,
  sha256                 CHAR(64) NOT NULL UNIQUE,
  storage_path           TEXT NOT NULL,
  unlocked_storage_path  TEXT,
  dek_wrapped            BYTEA NOT NULL,
  kek_id                 TEXT NOT NULL,
  pdf_password_encrypted BYTEA,
  page_count             INT,
  ocr_used               BOOLEAN NOT NULL DEFAULT FALSE,
  vision_used            BOOLEAN NOT NULL DEFAULT FALSE,
  text_chars             INT,
  classifier_source      TEXT CHECK (classifier_source IN ('RULE','LLM','USER')),
  classifier_confidence  NUMERIC(4,3),
  extraction_source      TEXT CHECK (extraction_source IN ('RULE','LLM','MIXED','USER')),
  extraction_confidence  NUMERIC(4,3),
  needs_review_reasons   TEXT[],
  last_error             TEXT,
  uploaded_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at           TIMESTAMPTZ,
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_document_member ON document(member_id);
CREATE INDEX ix_document_type_status ON document(doc_type, status);
CREATE INDEX ix_document_uploaded ON document(uploaded_at DESC);

CREATE TABLE ingestion_job (
  id            BIGSERIAL PRIMARY KEY,
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  step          TEXT NOT NULL CHECK (step IN ('FULL','FROM_TEXT','FROM_FIELDS','INDEX_ONLY')),
  status        TEXT NOT NULL CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
  attempt       INT NOT NULL DEFAULT 0,
  run_after     TIMESTAMPTZ NOT NULL DEFAULT now(),
  error         TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ
);
CREATE INDEX ix_ingestion_job_pick ON ingestion_job(status, run_after) WHERE status = 'QUEUED';

CREATE TABLE document_text (
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  page_no       INT NOT NULL,
  text_redacted TEXT NOT NULL,
  PRIMARY KEY (document_id, page_no)
);

CREATE TABLE document_chunk (
  id            BIGSERIAL PRIMARY KEY,
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  chunk_index   INT NOT NULL,
  page_no       INT,
  section       TEXT,
  text          TEXT NOT NULL,
  tsv           TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', text)) STORED,
  embedding     VECTOR(1536),
  UNIQUE (document_id, chunk_index)
);
CREATE INDEX ix_chunk_tsv ON document_chunk USING gin(tsv);
CREATE INDEX ix_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE document_summary (
  document_id   UUID PRIMARY KEY REFERENCES document(id) ON DELETE CASCADE,
  title         TEXT NOT NULL,
  summary       TEXT NOT NULL,
  people        JSONB NOT NULL DEFAULT '[]',   -- [{"name":"...","memberId":uuid|null,"role":"patient|policyholder|..."}]
  tags          TEXT[] NOT NULL DEFAULT '{}',
  key_facts     JSONB NOT NULL DEFAULT '{}',   -- free-form {label: value} the generic extractor found
  language      TEXT,
  model         TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_event (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  member_id     UUID REFERENCES family_member(id) ON DELETE SET NULL,
  kind          TEXT NOT NULL CHECK (kind IN ('APPOINTMENT','FOLLOW_UP','EXPIRY','DUE','REFILL','RENEWAL','OTHER')),
  label         TEXT NOT NULL,
  event_at      TIMESTAMPTZ NOT NULL,
  all_day       BOOLEAN NOT NULL DEFAULT TRUE,
  auto_reminder BOOLEAN NOT NULL DEFAULT TRUE,
  lead_days     INT[] NOT NULL DEFAULT '{1,0}',
  confidence    NUMERIC(4,3),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_document_event_doc ON document_event(document_id);

-- ---------- per-type field tables ----------
CREATE TABLE insurance_policy (
  document_id        UUID PRIMARY KEY REFERENCES document(id) ON DELETE CASCADE,
  member_id          UUID REFERENCES family_member(id) ON DELETE SET NULL,
  insurer            TEXT,
  policy_no          TEXT,
  policy_type        TEXT CHECK (policy_type IN ('HEALTH','LIFE_TERM','LIFE_ENDOWMENT','LIFE_ULIP','MOTOR','OTHER')),
  plan_name          TEXT,
  sum_assured        NUMERIC(14,2),
  premium_amount     NUMERIC(12,2),
  premium_frequency  TEXT CHECK (premium_frequency IN ('MONTHLY','QUARTERLY','HALF_YEARLY','YEARLY','SINGLE')),
  premium_due_date   DATE,
  policy_start       DATE,
  policy_expiry      DATE,
  nominee_name       TEXT,
  nominee_relation   TEXT,
  insured_members    JSONB NOT NULL DEFAULT '[]',
  field_meta         JSONB NOT NULL DEFAULT '{}',
  raw_extraction     JSONB,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_policy_expiry ON insurance_policy(policy_expiry);
CREATE INDEX ix_policy_premium_due ON insurance_policy(premium_due_date);

CREATE TABLE id_document (
  document_id        UUID PRIMARY KEY REFERENCES document(id) ON DELETE CASCADE,
  member_id          UUID REFERENCES family_member(id) ON DELETE SET NULL,
  id_type            TEXT NOT NULL CHECK (id_type IN ('AADHAAR','PASSPORT','DRIVING_LICENCE','PAN_CARD','VOTER_ID')),
  number_encrypted   BYTEA,
  number_masked      TEXT,
  number_hmac        CHAR(64),
  name_on_document   TEXT,
  dob                DATE,
  year_of_birth      INT,
  gender             TEXT CHECK (gender IN ('M','F','O')),
  issue_date         DATE,
  expiry_date        DATE,
  issuing_authority  TEXT,
  address_encrypted  BYTEA,
  address_masked     TEXT,
  field_meta         JSONB NOT NULL DEFAULT '{}',
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_id_document_expiry ON id_document(expiry_date);
CREATE INDEX ix_id_document_hmac ON id_document(number_hmac);

-- ---------- medical ----------
CREATE TABLE prescription (
  document_id     UUID PRIMARY KEY REFERENCES document(id) ON DELETE CASCADE,
  member_id       UUID REFERENCES family_member(id) ON DELETE SET NULL,   -- patient
  doctor_name     TEXT,
  hospital        TEXT,
  specialty       TEXT,
  visit_date      DATE,
  diagnosis       TEXT,
  follow_up_date  DATE,
  follow_up_note  TEXT,
  notes           TEXT,
  field_meta      JSONB NOT NULL DEFAULT '{}',
  raw_extraction  JSONB,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_prescription_follow_up ON prescription(follow_up_date);

CREATE TABLE medicine (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  prescription_id     UUID NOT NULL REFERENCES prescription(document_id) ON DELETE CASCADE,
  member_id           UUID REFERENCES family_member(id) ON DELETE SET NULL,
  name                TEXT NOT NULL,
  strength            TEXT,                       -- '500 mg'
  form                TEXT,                       -- tablet, syrup, drops
  dosage_instruction  TEXT,                       -- '1-0-1 after food'
  times_per_day       NUMERIC(4,2),
  duration_days       INT,
  quantity            INT,
  start_date          DATE,
  end_date            DATE,
  refill_due_date     DATE,                       -- end_date - 3 days, clamped to >= start_date
  active              BOOLEAN NOT NULL DEFAULT TRUE,
  confidence          NUMERIC(4,3),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_medicine_refill ON medicine(refill_due_date) WHERE active;

-- ---------- mutual funds ----------
CREATE TABLE scheme_master (
  isin              CHAR(12) PRIMARY KEY,
  amfi_code         TEXT,
  scheme_name       TEXT NOT NULL,
  amc               TEXT,
  scheme_type       TEXT CHECK (scheme_type IN ('OPEN_ENDED','CLOSE_ENDED','INTERVAL')),
  category          TEXT,
  category_bucket   TEXT CHECK (category_bucket IN
                      ('LARGE_CAP','LARGE_MID_CAP','MID_CAP','SMALL_CAP','FLEXI_MULTI_CAP','OTHER_EQUITY',
                       'DEBT','HYBRID','INDEX_ETF','SOLUTION','OTHER')),
  benchmark_index   TEXT,
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
  cost_value         NUMERIC(16,2),
  nav                NUMERIC(12,4),
  nav_date           DATE,
  value              NUMERIC(16,2),
  as_of_date         DATE NOT NULL,
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
  amount             NUMERIC(16,2),
  units              NUMERIC(18,3),
  nav                NUMERIC(12,4),
  balance_units      NUMERIC(18,3),
  dedupe_key         CHAR(64) NOT NULL UNIQUE,
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
  index_name  TEXT NOT NULL,
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

CREATE TABLE portfolio_job (
  id           BIGSERIAL PRIMARY KEY,
  kind         TEXT NOT NULL CHECK (kind IN ('NAV_REFRESH','NAV_BACKFILL','PERFORMANCE')),
  status       TEXT NOT NULL CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
  progress     INT NOT NULL DEFAULT 0,
  detail       TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at  TIMESTAMPTZ
);

-- ---------- reminders & messaging ----------
CREATE TABLE reminder_rule (
  rule_key     TEXT PRIMARY KEY,
  source_type  TEXT NOT NULL CHECK (source_type IN ('DOCUMENT','FAMILY_MEMBER','MEDICINE','DOCUMENT_EVENT')),
  doc_type     TEXT,
  date_field   TEXT NOT NULL,
  offsets_days INT[] NOT NULL,
  channels     TEXT[] NOT NULL,
  enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  description  TEXT
);

CREATE TABLE outbound_message (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel                TEXT NOT NULL CHECK (channel IN ('EMAIL','WHATSAPP')),
  provider               TEXT NOT NULL CHECK (provider IN ('SMTP','META_CLOUD','STUB')),
  recipient              TEXT NOT NULL,
  contact_id             UUID REFERENCES contact(id) ON DELETE SET NULL,
  contact_label          TEXT,
  subject                TEXT,
  template_name          TEXT,
  payload                JSONB NOT NULL DEFAULT '{}',
  attachment_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
  status                 TEXT NOT NULL CHECK (status IN ('QUEUED','SENDING','SENT','FAILED','SIMULATED','UNKNOWN')),
  provider_message_id    TEXT,
  attempts               INT NOT NULL DEFAULT 0,
  last_error             TEXT,
  dedupe_key             TEXT NOT NULL UNIQUE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at                TIMESTAMPTZ
);

CREATE TABLE reminder (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_key            TEXT REFERENCES reminder_rule(rule_key),     -- NULL for user/chat/voice-created
  origin              TEXT NOT NULL CHECK (origin IN ('RULE','DOCUMENT_EVENT','USER','CHAT','VOICE')),
  source_type         TEXT CHECK (source_type IN ('DOCUMENT','FAMILY_MEMBER','MEDICINE','DOCUMENT_EVENT')),
  source_id           UUID,
  member_id           UUID REFERENCES family_member(id) ON DELETE SET NULL,
  document_id         UUID REFERENCES document(id) ON DELETE CASCADE,
  event_at            TIMESTAMPTZ NOT NULL,
  offset_days         INT NOT NULL DEFAULT 0,
  fire_at             TIMESTAMPTZ NOT NULL,
  channel             TEXT NOT NULL CHECK (channel IN ('IN_APP','EMAIL','WHATSAPP')),
  status              TEXT NOT NULL CHECK (status IN
                        ('PENDING','SENDING','SENT','FAILED','DISMISSED','SNOOZED','SUPERSEDED','EXPIRED','UNKNOWN')),
  title               TEXT NOT NULL,
  body                TEXT NOT NULL,
  notes               TEXT,
  dedupe_key          TEXT NOT NULL UNIQUE,
  attempts            INT NOT NULL DEFAULT 0,
  claimed_at          TIMESTAMPTZ,
  sent_at             TIMESTAMPTZ,
  outbound_message_id UUID REFERENCES outbound_message(id) ON DELETE SET NULL,
  error               TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reminder_dispatch ON reminder(status, fire_at) WHERE status IN ('PENDING','SNOOZED');
CREATE INDEX ix_reminder_source ON reminder(source_type, source_id);
CREATE INDEX ix_reminder_document ON reminder(document_id);

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
  content        TEXT,
  tool_calls     JSONB,
  tool_call_id   TEXT,
  tool_name      TEXT,
  client_actions JSONB,
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
  payload       JSONB NOT NULL,
  summary       TEXT NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('PENDING','EXECUTED','CANCELLED','EXPIRED','FAILED')),
  result        JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  resolved_at   TIMESTAMPTZ
);

-- ---------- audit & LLM accounting ----------
CREATE TABLE audit_log (
  id                BIGSERIAL PRIMARY KEY,
  occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor             TEXT NOT NULL DEFAULT 'owner',
  origin            TEXT NOT NULL CHECK (origin IN ('UI','CHAT','SYSTEM')),
  action            TEXT NOT NULL CHECK (action IN
                      ('UPLOAD_DOCUMENT','DELETE_DOCUMENT','DOWNLOAD_ORIGINAL','REVEAL_FIELD','SHARE_DOCUMENT',
                       'FIELD_EDIT','PENDING_ACTION_CREATED','PENDING_ACTION_CONFIRMED','PENDING_ACTION_CANCELLED',
                       'LLM_CALL','VISION_CALL','REMINDER_SENT','REMINDER_CREATED')),
  document_id       UUID,
  document_label    TEXT,
  member_id         UUID,
  contact_id        UUID,
  contact_label     TEXT,
  field_name        TEXT,
  channel           TEXT,
  session_id        UUID,
  pending_action_id UUID,
  details           JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_audit_time ON audit_log(occurred_at DESC);
CREATE INDEX ix_audit_document ON audit_log(document_id);

CREATE OR REPLACE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'audit_log is append-only'; END $$;
CREATE TRIGGER trg_audit_log_immutable
  BEFORE UPDATE OR DELETE ON audit_log FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE TABLE llm_call (
  id                 BIGSERIAL PRIMARY KEY,
  purpose            TEXT NOT NULL,
  model              TEXT NOT NULL,
  document_id        UUID,
  session_id         UUID,
  input_tokens       INT,
  output_tokens      INT,
  redaction_counts   JSONB,
  image_sent         BOOLEAN NOT NULL DEFAULT FALSE,
  latency_ms         INT,
  status             TEXT NOT NULL,
  error              TEXT,
  openai_response_id TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- seed ----------
INSERT INTO reminder_rule (rule_key, source_type, doc_type, date_field, offsets_days, channels, description) VALUES
 ('POLICY_EXPIRY',   'DOCUMENT',       'INSURANCE_POLICY', 'policy_expiry',    '{30,7,1}', '{IN_APP,EMAIL}', 'Insurance policy expiry'),
 ('PREMIUM_DUE',     'DOCUMENT',       'INSURANCE_POLICY', 'premium_due_date', '{7,1}',    '{IN_APP,EMAIL}', 'Premium due'),
 ('PASSPORT_EXPIRY', 'DOCUMENT',       'PASSPORT',         'expiry_date',      '{90,30}',  '{IN_APP,EMAIL}', 'Passport expiry'),
 ('LICENCE_EXPIRY',  'DOCUMENT',       'DRIVING_LICENCE',  'expiry_date',      '{90,30}',  '{IN_APP,EMAIL}', 'Driving licence expiry'),
 ('BIRTHDAY',        'FAMILY_MEMBER',  NULL,               'dob',              '{7,0}',    '{IN_APP}',       'Family birthday'),
 ('FOLLOW_UP',       'DOCUMENT',       'PRESCRIPTION',     'follow_up_date',   '{1,0}',    '{IN_APP,EMAIL}', 'Doctor follow-up visit'),
 ('MEDICINE_REFILL', 'MEDICINE',       NULL,               'refill_due_date',  '{3,0}',    '{IN_APP}',       'Medicine running out'),
 ('DOCUMENT_EVENT',  'DOCUMENT_EVENT', NULL,               'event_at',         '{1,0}',    '{IN_APP}',       'Date found in a document');

INSERT INTO family_member (full_name, relation, is_self) VALUES ('Me', 'SELF', TRUE);
INSERT INTO user_settings (id) VALUES (1);
