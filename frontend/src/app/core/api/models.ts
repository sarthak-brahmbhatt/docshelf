// All DTO interfaces for the DocShelf REST API (design v0.1 §8 + build spec v0.2 §2.5); dates and timestamps are ISO strings.

// ---------------------------------------------------------------------------
// Enumerations (mirror the TEXT+CHECK columns in db/migration/V1__init.sql)
// ---------------------------------------------------------------------------

export type DocType =
  | 'INSURANCE_POLICY'
  | 'MF_CAS'
  | 'AADHAAR'
  | 'PASSPORT'
  | 'DRIVING_LICENCE'
  | 'PAN_CARD'
  | 'PRESCRIPTION'
  | 'MEDICAL_REPORT'
  | 'BILL_RECEIPT'
  | 'VOICE_NOTE'
  | 'NOTE'
  | 'OTHER'
  | 'UNKNOWN';

export const DOC_TYPES: readonly DocType[] = [
  'INSURANCE_POLICY',
  'MF_CAS',
  'AADHAAR',
  'PASSPORT',
  'DRIVING_LICENCE',
  'PAN_CARD',
  'PRESCRIPTION',
  'MEDICAL_REPORT',
  'BILL_RECEIPT',
  'VOICE_NOTE',
  'NOTE',
  'OTHER',
  'UNKNOWN',
] as const;

export type DocumentSource = 'UPLOAD' | 'CAMERA' | 'VOICE' | 'TEXT' | 'CHAT';

export type DocumentStatus =
  | 'UPLOADED'
  | 'NEEDS_PASSWORD'
  | 'EXTRACTING_TEXT'
  | 'CLASSIFYING'
  | 'EXTRACTING_FIELDS'
  | 'INDEXING'
  | 'READY'
  | 'NEEDS_REVIEW'
  | 'FAILED';

/** Statuses in which ingestion is still running and the UI should keep polling. */
export const DOCUMENT_IN_PROGRESS_STATUSES: readonly DocumentStatus[] = [
  'UPLOADED',
  'EXTRACTING_TEXT',
  'CLASSIFYING',
  'EXTRACTING_FIELDS',
  'INDEXING',
] as const;

export type ClassifierSource = 'RULE' | 'LLM' | 'USER';
export type ExtractionSource = 'RULE' | 'LLM' | 'MIXED' | 'USER' | 'VISION';

export type Relation =
  | 'SELF'
  | 'SPOUSE'
  | 'MOTHER'
  | 'FATHER'
  | 'SON'
  | 'DAUGHTER'
  | 'BROTHER'
  | 'SISTER'
  | 'OTHER';

export const RELATIONS: readonly Relation[] = [
  'SELF',
  'SPOUSE',
  'MOTHER',
  'FATHER',
  'SON',
  'DAUGHTER',
  'BROTHER',
  'SISTER',
  'OTHER',
] as const;

export type Gender = 'M' | 'F' | 'O';

export type PolicyType = 'HEALTH' | 'LIFE_TERM' | 'LIFE_ENDOWMENT' | 'LIFE_ULIP' | 'MOTOR' | 'OTHER';
export type PremiumFrequency = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY' | 'SINGLE';
export type IdType = 'AADHAAR' | 'PASSPORT' | 'DRIVING_LICENCE' | 'PAN_CARD' | 'VOTER_ID';

export type DocumentEventKind =
  | 'APPOINTMENT'
  | 'FOLLOW_UP'
  | 'EXPIRY'
  | 'DUE'
  | 'REFILL'
  | 'RENEWAL'
  | 'OTHER';

export type ReminderOrigin = 'RULE' | 'DOCUMENT_EVENT' | 'USER' | 'CHAT' | 'VOICE';
export type ReminderSourceType = 'DOCUMENT' | 'FAMILY_MEMBER' | 'MEDICINE' | 'DOCUMENT_EVENT';
export type ReminderChannel = 'IN_APP' | 'EMAIL' | 'WHATSAPP';
export type ReminderStatus =
  | 'PENDING'
  | 'SENDING'
  | 'SENT'
  | 'FAILED'
  | 'DISMISSED'
  | 'SNOOZED'
  | 'SUPERSEDED'
  | 'EXPIRED'
  | 'UNKNOWN';

export type ShareChannel = 'EMAIL' | 'WHATSAPP';
export type OutboundProvider = 'SMTP' | 'META_CLOUD' | 'STUB';
export type OutboundStatus = 'QUEUED' | 'SENDING' | 'SENT' | 'FAILED' | 'SIMULATED' | 'UNKNOWN';

export type CategoryBucket =
  | 'LARGE_CAP'
  | 'LARGE_MID_CAP'
  | 'MID_CAP'
  | 'SMALL_CAP'
  | 'FLEXI_MULTI_CAP'
  | 'OTHER_EQUITY'
  | 'DEBT'
  | 'HYBRID'
  | 'INDEX_ETF'
  | 'SOLUTION'
  | 'OTHER';

export type SchemeType = 'OPEN_ENDED' | 'CLOSE_ENDED' | 'INTERVAL';
export type SchemePlan = 'DIRECT' | 'REGULAR';
export type SchemeOptionType = 'GROWTH' | 'IDCW_PAYOUT' | 'IDCW_REINVEST';
export type SchemeSource = 'AMFI' | 'CAS' | 'USER';

export type TransactionType =
  | 'PURCHASE'
  | 'PURCHASE_SIP'
  | 'REDEMPTION'
  | 'SWITCH_IN'
  | 'SWITCH_OUT'
  | 'IDCW_PAYOUT'
  | 'IDCW_REINVEST'
  | 'STAMP_DUTY'
  | 'STT_TAX'
  | 'SEGREGATION'
  | 'MISC';

export type BenchmarkBasis = 'INDEX' | 'PEER_MEDIAN' | 'NOT_APPLICABLE';
export type Underperformed = 'YES' | 'NO' | 'INSUFFICIENT_DATA' | 'NOT_APPLICABLE';
export type PortfolioGroupBy = 'category' | 'amc' | 'member' | 'folio' | 'scheme';
export type PortfolioJobKind = 'NAV_REFRESH' | 'NAV_BACKFILL' | 'PERFORMANCE';
export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';

export type ChatRole = 'USER' | 'ASSISTANT' | 'TOOL' | 'DEVELOPER';
export type PendingActionType = 'SHARE_DOCUMENT' | 'REVEAL_FIELD';
export type PendingActionStatus = 'PENDING' | 'EXECUTED' | 'CANCELLED' | 'EXPIRED' | 'FAILED';

export type AuditOrigin = 'UI' | 'CHAT' | 'SYSTEM';
export type AuditAction =
  | 'UPLOAD_DOCUMENT'
  | 'DELETE_DOCUMENT'
  | 'DOWNLOAD_ORIGINAL'
  | 'REVEAL_FIELD'
  | 'SHARE_DOCUMENT'
  | 'FIELD_EDIT'
  | 'PENDING_ACTION_CREATED'
  | 'PENDING_ACTION_CONFIRMED'
  | 'PENDING_ACTION_CANCELLED'
  | 'LLM_CALL'
  | 'VISION_CALL'
  | 'REMINDER_SENT'
  | 'REMINDER_CREATED';

// ---------------------------------------------------------------------------
// Common envelopes
// ---------------------------------------------------------------------------

/** RFC 9457 Problem Details as emitted by the backend's ApiExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: FieldError[];
  /** Any additional extension members. */
  [extra: string]: unknown;
}

export interface FieldError {
  field: string;
  message: string;
}

/** Spring-style page envelope used by every paged list endpoint. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
}

/** Short member reference embedded in other DTOs (documents, reminders, holdings). */
export interface MemberRef {
  id: string;
  fullName: string;
  relation?: Relation;
}

// ---------------------------------------------------------------------------
// Family members and contacts (§8.1)
// ---------------------------------------------------------------------------

export interface FamilyMember {
  id: string;
  fullName: string;
  relation: Relation;
  isSelf: boolean;
  dob: string | null;
  gender: Gender | null;
  panMasked: string | null;
  email: string | null;
  phoneE164: string | null;
  notes: string | null;
  documentCount?: number;
  createdAt: string;
  updatedAt: string;
}

/** Create/replace body for a family member; `pan` is write-only and never echoed back. */
export interface FamilyMemberRequest {
  fullName: string;
  relation: Relation;
  isSelf?: boolean;
  dob?: string | null;
  gender?: Gender | null;
  pan?: string | null;
  email?: string | null;
  phoneE164?: string | null;
  notes?: string | null;
}

export interface Contact {
  id: string;
  fullName: string;
  relation: string | null;
  email: string | null;
  whatsappE164: string | null;
  whatsappOptedIn: boolean;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ContactRequest {
  fullName: string;
  relation?: string | null;
  email?: string | null;
  whatsappE164?: string | null;
  whatsappOptedIn?: boolean;
  notes?: string | null;
}

// ---------------------------------------------------------------------------
// Documents (§8.2, spec §2.5)
// ---------------------------------------------------------------------------

/** Per-field provenance returned in the `meta` map of every typed field object. */
export interface FieldMeta {
  source: ExtractionSource;
  confidence: number;
  page?: number | null;
}

export type FieldMetaMap = Record<string, FieldMeta>;

export interface InsuredMember {
  memberId: string | null;
  name: string;
}

export interface InsurancePolicyFields {
  insurer: string | null;
  policyNo: string | null;
  policyType: PolicyType | null;
  planName: string | null;
  sumAssured: number | null;
  premiumAmount: number | null;
  premiumFrequency: PremiumFrequency | null;
  premiumDueDate: string | null;
  policyStart: string | null;
  policyExpiry: string | null;
  nomineeName: string | null;
  nomineeRelation: string | null;
  insuredMembers: InsuredMember[];
  meta?: FieldMetaMap;
}

export interface IdDocumentFields {
  idType: IdType;
  /** Masked number, e.g. `XXXX XXXX 1234`. Full value only via the reveal endpoint. */
  numberMasked: string | null;
  nameOnDocument: string | null;
  dob: string | null;
  yearOfBirth: number | null;
  gender: Gender | null;
  issueDate: string | null;
  expiryDate: string | null;
  issuingAuthority: string | null;
  addressMasked: string | null;
  meta?: FieldMetaMap;
}

export interface CasScheme {
  isin: string;
  schemeName: string;
  closingUnits: number;
  nav: number | null;
  navDate: string | null;
  value: number | null;
  transactionCount: number;
}

export interface CasFolio {
  folioId: string | null;
  folioNo: string;
  amc: string;
  schemes: CasScheme[];
}

export interface CasReconciliation {
  ok: boolean;
  mismatches: string[];
}

export interface MfCasFields {
  statementFrom: string | null;
  statementTo: string | null;
  asOfDate: string | null;
  investorPanMasked: string | null;
  folios: CasFolio[];
  reconciliation: CasReconciliation;
  meta?: FieldMetaMap;
}

export interface Medicine {
  id: string;
  prescriptionId: string;
  memberId: string | null;
  name: string;
  strength: string | null;
  form: string | null;
  dosageInstruction: string | null;
  timesPerDay: number | null;
  durationDays: number | null;
  quantity: number | null;
  startDate: string | null;
  endDate: string | null;
  refillDueDate: string | null;
  active: boolean;
  confidence: number | null;
  updatedAt?: string;
}

/** Correction body for PUT /medical/medicines/{id}. */
export interface MedicineUpdateRequest {
  name?: string;
  strength?: string | null;
  form?: string | null;
  dosageInstruction?: string | null;
  timesPerDay?: number | null;
  durationDays?: number | null;
  quantity?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  active?: boolean;
}

export interface PrescriptionFields {
  doctorName: string | null;
  hospital: string | null;
  specialty: string | null;
  visitDate: string | null;
  diagnosis: string | null;
  followUpDate: string | null;
  followUpNote: string | null;
  notes: string | null;
  medicines: Medicine[];
  meta?: FieldMetaMap;
}

/** A prescription row as returned by /medical/prescriptions (fields + document context). */
export interface Prescription extends PrescriptionFields {
  documentId: string;
  documentTitle?: string;
  member: MemberRef | null;
  updatedAt?: string;
}

export interface SummaryPerson {
  name: string;
  memberId: string | null;
  role: string | null;
}

export interface DocumentEvent {
  id: string;
  documentId: string;
  memberId: string | null;
  kind: DocumentEventKind;
  label: string;
  eventAt: string;
  allDay: boolean;
  autoReminder: boolean;
  leadDays: number[];
  confidence: number | null;
  createdAt?: string;
}

/** Generic LLM extraction stored in document_summary (+ its document_event rows). */
export interface DocumentSummary {
  documentId: string;
  title: string;
  summary: string;
  people: SummaryPerson[];
  tags: string[];
  keyFacts: Record<string, string>;
  language: string | null;
  model: string | null;
  events: DocumentEvent[];
  createdAt?: string;
}

/** Fields for types without a dedicated table (MEDICAL_REPORT, BILL_RECEIPT, VOICE_NOTE, NOTE, OTHER). */
export interface GenericFields {
  title: string | null;
  summary: string | null;
  people: SummaryPerson[];
  tags: string[];
  keyFacts: Record<string, string>;
  events: DocumentEvent[];
  /** Transcript text for VOICE_NOTE documents, when present. */
  transcript?: string | null;
  meta?: FieldMetaMap;
}

export type DocumentFields =
  | InsurancePolicyFields
  | IdDocumentFields
  | MfCasFields
  | PrescriptionFields
  | GenericFields;

export interface ClassifierInfo {
  source: ClassifierSource | null;
  confidence: number | null;
}

export interface ExtractionInfo {
  source: ExtractionSource | null;
  confidence: number | null;
  needsReviewReasons: string[];
}

export interface Document {
  id: string;
  docType: DocType;
  source: DocumentSource;
  status: DocumentStatus;
  title: string;
  member: MemberRef | null;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  pageCount: number | null;
  ocrUsed: boolean;
  visionUsed: boolean;
  textChars?: number | null;
  classifier: ClassifierInfo;
  extraction: ExtractionInfo;
  uploadedAt: string;
  processedAt: string | null;
  updatedAt?: string;
  lastError: string | null;
  /** Typed, masked fields; absent until extraction has run. Discriminate on `docType`. */
  fields?: DocumentFields | null;
}

export interface DocumentListQuery extends PageQuery {
  type?: DocType;
  memberId?: string;
  status?: DocumentStatus;
  q?: string;
}

/** Multipart options for POST /documents. */
export interface UploadDocumentOptions {
  memberId?: string;
  typeHint?: DocType;
  title?: string;
  source?: 'UPLOAD' | 'CAMERA';
}

export interface TextDocumentRequest {
  title?: string;
  text: string;
  memberId?: string;
}

export interface VoiceDocumentOptions {
  memberId?: string;
  title?: string;
}

export interface DocumentPatch {
  title?: string;
  memberId?: string | null;
  docType?: DocType;
}

export interface DocumentPasswordRequest {
  password: string;
}

export type ReprocessFrom = 'TEXT' | 'FIELDS' | 'INDEX';

export interface ReprocessRequest {
  from: ReprocessFrom;
}

export interface RevealRequest {
  purpose?: string;
}

export interface RevealResponse {
  field: string;
  value: string;
  auditId: number;
  displaySeconds: number;
}

export interface DocumentTextPage {
  pageNo: number;
  text: string;
}

export interface DocumentText {
  documentId: string;
  pages: DocumentTextPage[];
}

export interface SearchQuery {
  q: string;
  memberId?: string;
  docType?: DocType;
  limit?: number;
}

export interface SearchHit {
  documentId: string;
  title: string;
  docType: DocType;
  memberName: string | null;
  snippet: string;
  score: number;
}

// ---------------------------------------------------------------------------
// Voice
// ---------------------------------------------------------------------------

export interface Transcript {
  text: string;
  language: string | null;
  durationSeconds: number | null;
}

// ---------------------------------------------------------------------------
// Reminders (§8.3, spec §2.5)
// ---------------------------------------------------------------------------

export interface Reminder {
  id: string;
  ruleKey: string | null;
  origin: ReminderOrigin;
  sourceType: ReminderSourceType | null;
  sourceId: string | null;
  documentId: string | null;
  documentTitle: string | null;
  member: MemberRef | null;
  /** Date (YYYY-MM-DD) of the underlying event. */
  eventDate: string;
  /** Full timestamp of the underlying event. */
  eventAt?: string;
  offsetDays: number;
  /** Date (YYYY-MM-DD) the reminder fires. */
  fireDate: string;
  /** Full timestamp the reminder fires. */
  fireAt?: string;
  channel: ReminderChannel;
  status: ReminderStatus;
  title: string;
  body: string;
  notes: string | null;
  sentAt: string | null;
  error: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReminderListQuery {
  from?: string;
  to?: string;
  status?: ReminderStatus[];
  memberId?: string;
  documentId?: string;
}

export interface ReminderCreateRequest {
  title: string;
  /** ISO datetime. */
  dueAt: string;
  notes?: string;
  memberId?: string;
  channels?: ReminderChannel[];
}

export interface ReminderUpdateRequest {
  title?: string;
  dueAt?: string;
  notes?: string | null;
}

export interface SnoozeRequest {
  /** YYYY-MM-DD, must be before the event date. */
  until: string;
}

export interface ReminderRule {
  ruleKey: string;
  sourceType: ReminderSourceType;
  docType: DocType | null;
  dateField: string;
  offsetsDays: number[];
  channels: ReminderChannel[];
  enabled: boolean;
  description: string | null;
}

export interface ReminderRuleUpdate {
  enabled: boolean;
  offsetsDays: number[];
  channels: ReminderChannel[];
}

export type ReminderRunPhase = 'MATERIALIZE' | 'DISPATCH' | 'BOTH';

export interface ReminderRunRequest {
  phase: ReminderRunPhase;
}

export interface ReminderRunResult {
  materialized?: number;
  superseded?: number;
  dispatched?: number;
  failed?: number;
  [counter: string]: number | undefined;
}

// ---------------------------------------------------------------------------
// Medical (spec §2.5)
// ---------------------------------------------------------------------------

export interface MedicineListQuery {
  memberId?: string;
  active?: boolean;
}

// ---------------------------------------------------------------------------
// Portfolio (§8.4)
// ---------------------------------------------------------------------------

export interface PerformanceRule {
  underperformToleranceP: number;
  horizons: string[];
  logic: 'AND' | 'OR';
}

export interface PortfolioGroup {
  key: string;
  label: string;
  invested: number;
  currentValue: number;
  weightPct: number;
  xirr: number | null;
  schemeCount: number;
}

export interface PortfolioSummary {
  asOfDate: string | null;
  invested: number;
  currentValue: number;
  absoluteGain: number;
  absoluteReturnPct: number;
  xirr: number | null;
  xirrDefined: boolean;
  groups: PortfolioGroup[];
  rule: PerformanceRule;
}

export interface PortfolioSummaryQuery {
  group_by: PortfolioGroupBy;
  memberId?: string;
}

export interface Performance {
  asOfDate: string;
  ret1y: number | null;
  cagr3y: number | null;
  benchRet1y: number | null;
  benchCagr3y: number | null;
  benchmarkName: string | null;
  basis: BenchmarkBasis;
  underperformed: Underperformed;
  peerCount?: number | null;
}

export interface HoldingRow {
  folioId?: string;
  folioNo: string;
  member: MemberRef | null;
  isin: string;
  schemeName: string;
  amc: string;
  categoryBucket: CategoryBucket | null;
  units: number;
  nav: number | null;
  navDate: string | null;
  value: number | null;
  costValue: number | null;
  xirr: number | null;
  performance: Performance | null;
}

export interface HoldingsQuery {
  memberId?: string;
  underperformedOnly?: boolean;
}

export interface Scheme {
  isin: string;
  amfiCode: string | null;
  schemeName: string;
  amc: string | null;
  schemeType: SchemeType | null;
  category: string | null;
  categoryBucket: CategoryBucket | null;
  benchmarkIndex: string | null;
  plan: SchemePlan | null;
  optionType: SchemeOptionType | null;
  source: SchemeSource;
  updatedAt?: string;
}

export interface SchemeUpdateRequest {
  categoryBucket?: CategoryBucket | null;
  benchmarkIndex?: string | null;
}

export interface NavPoint {
  date: string;
  nav: number;
}

export interface Transaction {
  id: number;
  folioId: string;
  folioNo?: string;
  isin: string;
  schemeName?: string;
  txnDate: string;
  description: string;
  txnType: TransactionType;
  amount: number | null;
  units: number | null;
  nav: number | null;
  balanceUnits: number | null;
  sourceDocumentId: string | null;
}

export interface TransactionsQuery extends PageQuery {
  isin?: string;
  folioId?: string;
  from?: string;
  to?: string;
}

export interface HoldingDetail {
  scheme: Scheme;
  holdings: HoldingRow[];
  transactions: Transaction[];
  navSeries: NavPoint[];
  performance: Performance | null;
  xirr: number | null;
}

export interface HoldingDetailQuery {
  folioId?: string;
  from?: string;
  to?: string;
}

export interface SchemePerformance extends Performance {
  isin: string;
  schemeName?: string;
}

export interface PerformanceResponse {
  asOfDate: string | null;
  rule: PerformanceRule;
  schemes: SchemePerformance[];
}

export interface PortfolioJob {
  id: number;
  kind: PortfolioJobKind;
  status: JobStatus;
  progress: number;
  detail: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface NavBackfillRequest {
  isins?: string[];
}

export interface BenchmarkImportResult {
  imported: number;
  skipped: number;
  errors?: { line: number; message: string }[];
}

// ---------------------------------------------------------------------------
// Chat and pending actions (§5, §8.5, spec §2.5)
// ---------------------------------------------------------------------------

export interface ChatSession {
  id: string;
  title: string;
  model: string;
  createdAt: string;
  lastMessageAt: string | null;
}

export interface ChatSessionCreateRequest {
  title?: string;
}

export interface ToolCallSummary {
  name: string;
  argumentsSummary: string;
}

export interface ScheduleNotificationAction {
  type: 'SCHEDULE_NOTIFICATION';
  reminderId: string;
  title: string;
  /** ISO datetime at which the phone should notify. */
  at: string;
  body?: string;
}

export interface AddCalendarEventAction {
  type: 'ADD_CALENDAR_EVENT';
  title: string;
  startAt: string;
  endAt?: string | null;
  notes?: string | null;
}

/** Side effects the client mirrors locally after a chat turn (spec §2.5). */
export type ClientAction = ScheduleNotificationAction | AddCalendarEventAction;

export interface ChatMessage {
  id: string;
  sessionId?: string;
  seq?: number;
  role: ChatRole;
  content: string | null;
  toolCalls?: ToolCallSummary[] | null;
  toolName?: string | null;
  clientActions?: ClientAction[] | null;
  createdAt: string;
}

export interface ChatMessageRequest {
  content: string;
}

export interface ChatUsage {
  inputTokens: number;
  outputTokens: number;
}

export interface PendingAction {
  id: string;
  type: PendingActionType;
  summary: string;
  status: PendingActionStatus;
  expiresAt: string;
  sessionId?: string | null;
  messageId?: string | null;
  createdAt?: string;
  resolvedAt?: string | null;
  result?: PendingActionResult | null;
}

export interface ShareExecutionResult {
  channel: ShareChannel;
  providerMessageId: string | null;
  simulated: boolean;
  outboundMessageId: string;
}

export type PendingActionResult = ShareExecutionResult | RevealResponse;

/** Response of POST /pending-actions/{id}/confirm. */
export interface PendingActionConfirmResponse {
  id: string;
  status: PendingActionStatus;
  result: PendingActionResult | null;
}

/** Response of POST /chat/sessions/{id}/messages. */
export interface ChatTurnResponse {
  message: ChatMessage;
  toolCalls: ToolCallSummary[];
  pendingAction: PendingAction | null;
  clientActions: ClientAction[];
  usage: ChatUsage | null;
}

// ---------------------------------------------------------------------------
// Shares, audit, system, settings (§8.6, spec §2.5)
// ---------------------------------------------------------------------------

export interface ShareRequest {
  documentId: string;
  contactId: string;
  channel: ShareChannel;
  protectAttachment?: boolean;
}

/** An outbound_message row (share result / share history entry). */
export interface ShareResult {
  id: string;
  channel: ShareChannel;
  provider: OutboundProvider;
  recipient: string;
  contactId: string | null;
  contactLabel: string | null;
  subject: string | null;
  attachmentDocumentId: string | null;
  status: OutboundStatus;
  providerMessageId: string | null;
  simulated?: boolean;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
  /** Passphrase shown once when `protectAttachment` was requested. */
  attachmentPassphrase?: string | null;
}

export interface AuditEntry {
  id: number;
  occurredAt: string;
  actor: string;
  origin: AuditOrigin;
  action: AuditAction;
  documentId: string | null;
  documentLabel: string | null;
  memberId: string | null;
  contactId: string | null;
  contactLabel: string | null;
  fieldName: string | null;
  channel: string | null;
  sessionId: string | null;
  pendingActionId: string | null;
  details: Record<string, unknown>;
}

export interface AuditQuery extends PageQuery {
  from?: string;
  to?: string;
  action?: AuditAction[];
  documentId?: string;
}

export interface HealthCheck {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  detail?: string | null;
}

export interface SystemHealth {
  status: 'UP' | 'DOWN' | 'DEGRADED';
  version?: string;
  db: HealthCheck;
  blobVolume: HealthCheck;
  openaiKeyPresent: boolean;
  smtp: HealthCheck;
  lastNavRefresh: string | null;
  serverTime?: string;
}

export interface LlmUsageRow {
  day: string;
  purpose: string;
  model?: string;
  calls: number;
  inputTokens: number;
  outputTokens: number;
}

export interface LlmUsage {
  from: string;
  to: string;
  rows: LlmUsageRow[];
  totalInputTokens: number;
  totalOutputTokens: number;
}

export interface UserSettings {
  reminderEmail: string | null;
  ownerWhatsapp: string | null;
  voiceLanguage: string;
  updatedAt?: string;
}

export interface UserSettingsUpdate {
  reminderEmail?: string | null;
  ownerWhatsapp?: string | null;
  voiceLanguage?: string;
}

// ---------------------------------------------------------------------------
// Type guards for the DocumentFields union (discriminate on Document.docType)
// ---------------------------------------------------------------------------

export const ID_DOC_TYPES: readonly DocType[] = ['AADHAAR', 'PASSPORT', 'DRIVING_LICENCE', 'PAN_CARD'] as const;

export function isIdDocType(type: DocType): boolean {
  return ID_DOC_TYPES.includes(type);
}

export function insuranceFields(doc: Document): InsurancePolicyFields | null {
  return doc.docType === 'INSURANCE_POLICY' && doc.fields ? (doc.fields as InsurancePolicyFields) : null;
}

export function idDocumentFields(doc: Document): IdDocumentFields | null {
  return isIdDocType(doc.docType) && doc.fields ? (doc.fields as IdDocumentFields) : null;
}

export function casFields(doc: Document): MfCasFields | null {
  return doc.docType === 'MF_CAS' && doc.fields ? (doc.fields as MfCasFields) : null;
}

export function prescriptionFields(doc: Document): PrescriptionFields | null {
  return doc.docType === 'PRESCRIPTION' && doc.fields ? (doc.fields as PrescriptionFields) : null;
}

export function genericFields(doc: Document): GenericFields | null {
  const generic: readonly DocType[] = ['MEDICAL_REPORT', 'BILL_RECEIPT', 'VOICE_NOTE', 'NOTE', 'OTHER', 'UNKNOWN'];
  return generic.includes(doc.docType) && doc.fields ? (doc.fields as GenericFields) : null;
}
