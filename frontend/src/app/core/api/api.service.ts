// Typed HTTP client for the DocShelf REST API: one method per endpoint (design v0.1 §8 + build spec v0.2 §2.5), grouped by resource.
import { HttpClient, HttpContext, HttpEvent, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SettingsService } from '../settings/settings.service';
import { apiUrl, toParams } from './api-base';
import { SILENT_ERRORS } from './error.interceptor';
import {
  AuditEntry,
  AuditQuery,
  BenchmarkImportResult,
  ChatMessage,
  ChatMessageRequest,
  ChatSession,
  ChatSessionCreateRequest,
  ChatTurnResponse,
  Contact,
  ContactRequest,
  Document,
  DocumentListQuery,
  DocumentPasswordRequest,
  DocumentPatch,
  DocumentSummary,
  DocumentText,
  FamilyMember,
  FamilyMemberRequest,
  HoldingDetail,
  HoldingDetailQuery,
  HoldingRow,
  HoldingsQuery,
  LlmUsage,
  Medicine,
  MedicineListQuery,
  MedicineUpdateRequest,
  NavBackfillRequest,
  PageResponse,
  PendingAction,
  PendingActionConfirmResponse,
  PerformanceResponse,
  PortfolioGroupBy,
  PortfolioJob,
  PortfolioSummary,
  Prescription,
  Reminder,
  ReminderCreateRequest,
  ReminderListQuery,
  ReminderRule,
  ReminderRuleUpdate,
  ReminderRunRequest,
  ReminderRunResult,
  ReminderUpdateRequest,
  ReprocessRequest,
  RevealRequest,
  RevealResponse,
  Scheme,
  SchemeUpdateRequest,
  SearchHit,
  SearchQuery,
  ShareRequest,
  ShareResult,
  SnoozeRequest,
  SystemHealth,
  TextDocumentRequest,
  Transaction,
  TransactionsQuery,
  Transcript,
  UploadDocumentOptions,
  UserSettings,
  UserSettingsUpdate,
  VoiceDocumentOptions,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly settings = inject(SettingsService);

  /** Absolute URL for an API path, using the current base URL setting. */
  url(path: string): string {
    return apiUrl(this.settings.apiBaseUrl(), path);
  }

  // ---------------------------------------------------------------------------
  // Family members
  // ---------------------------------------------------------------------------

  listMembers(): Observable<FamilyMember[]> {
    return this.http.get<FamilyMember[]>(this.url('/members'));
  }

  createMember(body: FamilyMemberRequest): Observable<FamilyMember> {
    return this.http.post<FamilyMember>(this.url('/members'), body);
  }

  getMember(id: string): Observable<FamilyMember> {
    return this.http.get<FamilyMember>(this.url(`/members/${id}`));
  }

  updateMember(id: string, body: FamilyMemberRequest): Observable<FamilyMember> {
    return this.http.put<FamilyMember>(this.url(`/members/${id}`), body);
  }

  deleteMember(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/members/${id}`));
  }

  // ---------------------------------------------------------------------------
  // Contacts
  // ---------------------------------------------------------------------------

  listContacts(): Observable<Contact[]> {
    return this.http.get<Contact[]>(this.url('/contacts'));
  }

  createContact(body: ContactRequest): Observable<Contact> {
    return this.http.post<Contact>(this.url('/contacts'), body);
  }

  getContact(id: string): Observable<Contact> {
    return this.http.get<Contact>(this.url(`/contacts/${id}`));
  }

  updateContact(id: string, body: ContactRequest): Observable<Contact> {
    return this.http.put<Contact>(this.url(`/contacts/${id}`), body);
  }

  deleteContact(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/contacts/${id}`));
  }

  // ---------------------------------------------------------------------------
  // Documents
  // ---------------------------------------------------------------------------

  /**
   * Multipart upload of any file with progress events. Subscribe and switch on `event.type`
   * (HttpEventType.UploadProgress / HttpEventType.Response). The 409 duplicate case arrives as an error
   * whose body is the existing Document.
   */
  uploadDocument(file: File | Blob, opts: UploadDocumentOptions = {}): Observable<HttpEvent<Document>> {
    const form = new FormData();
    const filename = file instanceof File ? file.name : `capture-${Date.now()}.${extensionFor(file.type)}`;
    form.append('file', file, filename);
    if (opts.memberId) form.append('memberId', opts.memberId);
    if (opts.typeHint) form.append('typeHint', opts.typeHint);
    if (opts.title) form.append('title', opts.title);
    if (opts.source) form.append('source', opts.source);
    return this.http.post<Document>(this.url('/documents'), form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  /** Typed text → NOTE document. */
  createTextDocument(body: TextDocumentRequest): Observable<Document> {
    return this.http.post<Document>(this.url('/documents/text'), body);
  }

  /** Recorded audio → VOICE_NOTE document (transcribed and indexed server-side), with upload progress. */
  createVoiceDocument(
    audio: Blob,
    mimeType: string,
    opts: VoiceDocumentOptions = {},
  ): Observable<HttpEvent<Document>> {
    const form = new FormData();
    form.append('audio', audio, `voice-note-${Date.now()}.${extensionFor(mimeType)}`);
    if (opts.memberId) form.append('memberId', opts.memberId);
    if (opts.title) form.append('title', opts.title);
    return this.http.post<Document>(this.url('/documents/voice'), form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  listDocuments(query: DocumentListQuery = {}): Observable<PageResponse<Document>> {
    return this.http.get<PageResponse<Document>>(this.url('/documents'), { params: toParams({ ...query }) });
  }

  getDocument(id: string, opts: { silent?: boolean } = {}): Observable<Document> {
    return this.http.get<Document>(this.url(`/documents/${id}`), { context: silentContext(opts.silent) });
  }

  patchDocument(id: string, body: DocumentPatch): Observable<Document> {
    return this.http.patch<Document>(this.url(`/documents/${id}`), body);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/documents/${id}`));
  }

  /** Streams the decrypted original as a Blob (audited as DOWNLOAD_ORIGINAL). */
  getDocumentFile(id: string): Observable<Blob> {
    return this.http.get(this.url(`/documents/${id}/file`), { responseType: 'blob' });
  }

  /** URL of the original file, for `<iframe>`/`<img>` usage (the auth header cannot be attached; prefer getDocumentFile). */
  documentFileUrl(id: string): string {
    return this.url(`/documents/${id}/file`);
  }

  submitDocumentPassword(id: string, body: DocumentPasswordRequest): Observable<Document> {
    return this.http.post<Document>(this.url(`/documents/${id}/password`), body);
  }

  reprocessDocument(id: string, body: ReprocessRequest): Observable<Document> {
    return this.http.post<Document>(this.url(`/documents/${id}/reprocess`), body);
  }

  getDocumentFields<T = Record<string, unknown>>(id: string): Observable<T> {
    return this.http.get<T>(this.url(`/documents/${id}/fields`));
  }

  updateDocumentFields<T = Record<string, unknown>>(id: string, body: T): Observable<T> {
    return this.http.put<T>(this.url(`/documents/${id}/fields`), body);
  }

  /** Full value of a sensitive field, once, audited. Show for `displaySeconds` then re-mask. */
  revealField(id: string, field: string, body: RevealRequest = {}): Observable<RevealResponse> {
    return this.http.post<RevealResponse>(this.url(`/documents/${id}/fields/${field}/reveal`), body);
  }

  getDocumentAudit(id: string): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(this.url(`/documents/${id}/audit`));
  }

  getDocumentSummary(id: string): Observable<DocumentSummary> {
    return this.http.get<DocumentSummary>(this.url(`/documents/${id}/summary`));
  }

  getDocumentText(id: string): Observable<DocumentText> {
    return this.http.get<DocumentText>(this.url(`/documents/${id}/text`));
  }

  // ---------------------------------------------------------------------------
  // Search and voice
  // ---------------------------------------------------------------------------

  search(query: SearchQuery): Observable<SearchHit[]> {
    return this.http.get<SearchHit[]>(this.url('/search'), { params: toParams({ ...query }) });
  }

  /** Transcribes a voice command; nothing is stored server-side. */
  transcribe(audio: Blob, mimeType?: string, languageHint?: string): Observable<Transcript> {
    const type = mimeType || audio.type || 'audio/webm';
    const form = new FormData();
    form.append('audio', audio, `command-${Date.now()}.${extensionFor(type)}`);
    if (languageHint) form.append('language', languageHint);
    return this.http.post<Transcript>(this.url('/voice/transcribe'), form);
  }

  // ---------------------------------------------------------------------------
  // Reminders
  // ---------------------------------------------------------------------------

  listReminders(query: ReminderListQuery = {}): Observable<Reminder[]> {
    return this.http.get<Reminder[]>(this.url('/reminders'), { params: toParams({ ...query }) });
  }

  createReminder(body: ReminderCreateRequest): Observable<Reminder> {
    return this.http.post<Reminder>(this.url('/reminders'), body);
  }

  getReminder(id: string): Observable<Reminder> {
    return this.http.get<Reminder>(this.url(`/reminders/${id}`));
  }

  updateReminder(id: string, body: ReminderUpdateRequest): Observable<Reminder> {
    return this.http.put<Reminder>(this.url(`/reminders/${id}`), body);
  }

  dismissReminder(id: string): Observable<Reminder> {
    return this.http.post<Reminder>(this.url(`/reminders/${id}/dismiss`), {});
  }

  snoozeReminder(id: string, body: SnoozeRequest): Observable<Reminder> {
    return this.http.post<Reminder>(this.url(`/reminders/${id}/snooze`), body);
  }

  resendReminder(id: string): Observable<Reminder> {
    return this.http.post<Reminder>(this.url(`/reminders/${id}/resend`), {});
  }

  listReminderRules(): Observable<ReminderRule[]> {
    return this.http.get<ReminderRule[]>(this.url('/reminder-rules'));
  }

  updateReminderRule(ruleKey: string, body: ReminderRuleUpdate): Observable<ReminderRule> {
    return this.http.put<ReminderRule>(this.url(`/reminder-rules/${ruleKey}`), body);
  }

  runReminders(body: ReminderRunRequest): Observable<ReminderRunResult> {
    return this.http.post<ReminderRunResult>(this.url('/reminders/run'), body);
  }

  // ---------------------------------------------------------------------------
  // Medical
  // ---------------------------------------------------------------------------

  listPrescriptions(memberId?: string): Observable<Prescription[]> {
    return this.http.get<Prescription[]>(this.url('/medical/prescriptions'), { params: toParams({ memberId }) });
  }

  getPrescription(documentId: string): Observable<Prescription> {
    return this.http.get<Prescription>(this.url(`/medical/prescriptions/${documentId}`));
  }

  listMedicines(query: MedicineListQuery = {}): Observable<Medicine[]> {
    return this.http.get<Medicine[]>(this.url('/medical/medicines'), { params: toParams({ ...query }) });
  }

  updateMedicine(id: string, body: MedicineUpdateRequest): Observable<Medicine> {
    return this.http.put<Medicine>(this.url(`/medical/medicines/${id}`), body);
  }

  // ---------------------------------------------------------------------------
  // Portfolio
  // ---------------------------------------------------------------------------

  getPortfolioSummary(groupBy: PortfolioGroupBy = 'category', memberId?: string): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(this.url('/portfolio/summary'), {
      params: toParams({ group_by: groupBy, memberId }),
    });
  }

  listHoldings(query: HoldingsQuery = {}): Observable<HoldingRow[]> {
    return this.http.get<HoldingRow[]>(this.url('/portfolio/holdings'), { params: toParams({ ...query }) });
  }

  getHolding(isin: string, query: HoldingDetailQuery = {}): Observable<HoldingDetail> {
    return this.http.get<HoldingDetail>(this.url(`/portfolio/holdings/${isin}`), { params: toParams({ ...query }) });
  }

  listTransactions(query: TransactionsQuery = {}): Observable<PageResponse<Transaction>> {
    return this.http.get<PageResponse<Transaction>>(this.url('/portfolio/transactions'), {
      params: toParams({ ...query }),
    });
  }

  getPerformance(): Observable<PerformanceResponse> {
    return this.http.get<PerformanceResponse>(this.url('/portfolio/performance'));
  }

  refreshNav(): Observable<PortfolioJob> {
    return this.http.post<PortfolioJob>(this.url('/portfolio/nav/refresh'), {});
  }

  backfillNav(body: NavBackfillRequest = {}): Observable<PortfolioJob> {
    return this.http.post<PortfolioJob>(this.url('/portfolio/nav/backfill'), body);
  }

  listPortfolioJobs(): Observable<PortfolioJob[]> {
    return this.http.get<PortfolioJob[]>(this.url('/portfolio/jobs'));
  }

  /** Multipart CSV `index_name,date,value` with upload progress. */
  importBenchmarks(file: File | Blob): Observable<HttpEvent<BenchmarkImportResult>> {
    const form = new FormData();
    form.append('file', file, file instanceof File ? file.name : 'benchmarks.csv');
    return this.http.post<BenchmarkImportResult>(this.url('/portfolio/benchmarks/import'), form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  getScheme(isin: string): Observable<Scheme> {
    return this.http.get<Scheme>(this.url(`/schemes/${isin}`));
  }

  updateScheme(isin: string, body: SchemeUpdateRequest): Observable<Scheme> {
    return this.http.put<Scheme>(this.url(`/schemes/${isin}`), body);
  }

  // ---------------------------------------------------------------------------
  // Chat and pending actions
  // ---------------------------------------------------------------------------

  createChatSession(body: ChatSessionCreateRequest = {}): Observable<ChatSession> {
    return this.http.post<ChatSession>(this.url('/chat/sessions'), body);
  }

  listChatSessions(): Observable<ChatSession[]> {
    return this.http.get<ChatSession[]>(this.url('/chat/sessions'));
  }

  listChatMessages(sessionId: string, includeTool = false): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(this.url(`/chat/sessions/${sessionId}/messages`), {
      params: toParams({ includeTool: includeTool ? true : undefined }),
    });
  }

  /** Runs one chat turn (server-side tool loop). `idempotencyKey` guards against double-submits. */
  sendChatMessage(
    sessionId: string,
    body: ChatMessageRequest,
    idempotencyKey?: string,
  ): Observable<ChatTurnResponse> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<ChatTurnResponse>(this.url(`/chat/sessions/${sessionId}/messages`), body, { headers });
  }

  deleteChatSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(this.url(`/chat/sessions/${sessionId}`));
  }

  getPendingAction(id: string): Observable<PendingAction> {
    return this.http.get<PendingAction>(this.url(`/pending-actions/${id}`));
  }

  confirmPendingAction(id: string): Observable<PendingActionConfirmResponse> {
    return this.http.post<PendingActionConfirmResponse>(this.url(`/pending-actions/${id}/confirm`), {});
  }

  cancelPendingAction(id: string): Observable<PendingAction> {
    return this.http.post<PendingAction>(this.url(`/pending-actions/${id}/cancel`), {});
  }

  // ---------------------------------------------------------------------------
  // Shares, audit, system, settings
  // ---------------------------------------------------------------------------

  createShare(body: ShareRequest): Observable<ShareResult> {
    return this.http.post<ShareResult>(this.url('/shares'), body);
  }

  listShares(): Observable<ShareResult[]> {
    return this.http.get<ShareResult[]>(this.url('/shares'));
  }

  listAudit(query: AuditQuery = {}): Observable<PageResponse<AuditEntry>> {
    return this.http.get<PageResponse<AuditEntry>>(this.url('/audit'), { params: toParams({ ...query }) });
  }

  /** Health probe; `silent` suppresses the global error snackbar (used by Settings "Test connection"). */
  getSystemHealth(opts: { silent?: boolean } = {}): Observable<SystemHealth> {
    return this.http.get<SystemHealth>(this.url('/system/health'), { context: silentContext(opts.silent) });
  }

  getLlmUsage(from?: string, to?: string): Observable<LlmUsage> {
    return this.http.get<LlmUsage>(this.url('/system/llm-usage'), { params: toParams({ from, to }) });
  }

  getUserSettings(opts: { silent?: boolean } = {}): Observable<UserSettings> {
    return this.http.get<UserSettings>(this.url('/settings'), { context: silentContext(opts.silent) });
  }

  updateUserSettings(body: UserSettingsUpdate): Observable<UserSettings> {
    return this.http.put<UserSettings>(this.url('/settings'), body);
  }
}

function silentContext(silent?: boolean): HttpContext | undefined {
  return silent ? new HttpContext().set(SILENT_ERRORS, true) : undefined;
}

/** Best-effort file extension for a MIME type (used to name captured blobs). */
export function extensionFor(mimeType: string): string {
  const type = (mimeType || '').toLowerCase().split(';')[0].trim();
  const map: Record<string, string> = {
    'image/jpeg': 'jpg',
    'image/png': 'png',
    'image/webp': 'webp',
    'image/heic': 'heic',
    'application/pdf': 'pdf',
    'audio/webm': 'webm',
    'audio/ogg': 'ogg',
    'audio/mp4': 'm4a',
    'audio/aac': 'aac',
    'audio/mpeg': 'mp3',
    'audio/wav': 'wav',
    'audio/x-wav': 'wav',
    'text/plain': 'txt',
  };
  if (map[type]) return map[type];
  const slash = type.indexOf('/');
  return slash >= 0 ? type.slice(slash + 1).replace(/^x-/, '') || 'bin' : 'bin';
}
