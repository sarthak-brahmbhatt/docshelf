// Unit tests for ApiService: URL building from settings, auth header, query params, multipart uploads with progress.
import { HttpEventType, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SettingsService } from '../settings/settings.service';
import { ApiService, extensionFor } from './api.service';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { Document } from './models';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;
  let settings: SettingsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
    settings = TestBed.inject(SettingsService);
    settings.apiBaseUrl.set('');
    settings.apiToken.set('');
  });

  afterEach(() => http.verify());

  it('uses a same-origin /api/v1 path when the base URL is empty', () => {
    api.listMembers().subscribe();
    const req = http.expectOne('/api/v1/members');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('prefixes the configured base URL and adds the bearer token', () => {
    settings.apiBaseUrl.set('http://192.168.1.10:8080');
    settings.apiToken.set('tok-123');
    api.getSystemHealth().subscribe();
    const req = http.expectOne('http://192.168.1.10:8080/api/v1/system/health');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-123');
    req.flush({ status: 'UP' });
  });

  it('serialises query params and repeats array params', () => {
    api.listReminders({ from: '2026-09-01', to: '2026-09-30', status: ['PENDING', 'SNOOZED'], memberId: undefined }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/reminders');
    expect(req.request.params.get('from')).toBe('2026-09-01');
    expect(req.request.params.getAll('status')).toEqual(['PENDING', 'SNOOZED']);
    expect(req.request.params.has('memberId')).toBe(false);
    req.flush([]);
  });

  it('uploads documents as multipart with progress events', () => {
    const file = new File(['hello'], 'policy.pdf', { type: 'application/pdf' });
    const events: HttpEventType[] = [];
    let response: Document | null = null;
    api.uploadDocument(file, { memberId: 'm1', typeHint: 'INSURANCE_POLICY', source: 'UPLOAD' }).subscribe((ev) => {
      events.push(ev.type);
      if (ev.type === HttpEventType.Response) response = ev.body;
    });
    const req = http.expectOne('/api/v1/documents');
    expect(req.request.method).toBe('POST');
    expect(req.request.reportProgress).toBe(true);
    const body = req.request.body as FormData;
    expect(body.get('file')).toBeInstanceOf(File);
    expect(body.get('memberId')).toBe('m1');
    expect(body.get('typeHint')).toBe('INSURANCE_POLICY');
    expect(body.get('source')).toBe('UPLOAD');
    req.event({ type: HttpEventType.UploadProgress, loaded: 2, total: 5 });
    req.flush({ id: 'd1', status: 'UPLOADED' });
    expect(events).toContain(HttpEventType.UploadProgress);
    expect(events).toContain(HttpEventType.Response);
    expect(response!.id).toBe('d1');
  });

  it('posts voice commands to /voice/transcribe with the audio blob', () => {
    const blob = new Blob(['audio'], { type: 'audio/webm' });
    api.transcribe(blob, 'audio/webm', 'en-IN').subscribe();
    const req = http.expectOne('/api/v1/voice/transcribe');
    const body = req.request.body as FormData;
    expect(body.get('audio')).toBeInstanceOf(Blob);
    expect(body.get('language')).toBe('en-IN');
    req.flush({ text: 'hi', language: 'en-IN', durationSeconds: 1 });
  });

  it('sends chat messages with an Idempotency-Key header', () => {
    api.sendChatMessage('s1', { content: 'hello' }, 'key-1').subscribe();
    const req = http.expectOne('/api/v1/chat/sessions/s1/messages');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
    req.flush({ message: {}, toolCalls: [], pendingAction: null, clientActions: [], usage: null });
  });

  it('requests the original file as a blob', () => {
    api.getDocumentFile('d1').subscribe();
    const req = http.expectOne('/api/v1/documents/d1/file');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('rethrows HTTP errors so callers can handle them', () => {
    let status = 0;
    api.getDocument('missing', { silent: true }).subscribe({ error: (e) => (status = e.status) });
    http.expectOne('/api/v1/documents/missing').flush({ title: 'Not found', status: 404 }, { status: 404, statusText: 'Not Found' });
    expect(status).toBe(404);
  });
});

describe('extensionFor', () => {
  it('maps common MIME types', () => {
    expect(extensionFor('image/jpeg')).toBe('jpg');
    expect(extensionFor('audio/webm;codecs=opus')).toBe('webm');
    expect(extensionFor('application/pdf')).toBe('pdf');
    expect(extensionFor('audio/x-m4a')).toBe('m4a');
    expect(extensionFor('')).toBe('bin');
  });
});
