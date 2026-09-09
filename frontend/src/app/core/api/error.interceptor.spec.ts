// Unit tests for the error interceptor: ProblemDetail → toast, 401 → /settings redirect, silent context.
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ToastService } from '../ui/toast.service';
import { errorInterceptor } from './error.interceptor';
import { ApiService } from './api.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let router: Router;
  let errors: string[];

  beforeEach(() => {
    errors = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'settings', children: [] }]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: { error: (m: string) => errors.push(m), info: () => undefined, success: () => undefined } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => backend.verify());

  it('shows the ProblemDetail detail and rethrows', () => {
    let caught = 0;
    http.get('/api/v1/members/x').subscribe({ error: (e) => (caught = e.status) });
    backend.expectOne('/api/v1/members/x').flush(
      { type: 'https://docshelf.local/errors/not-found', title: 'Not found', status: 404, detail: 'Member x does not exist' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(caught).toBe(404);
    expect(errors).toEqual(['Member x does not exist']);
  });

  it('includes validation errors in the message', () => {
    http.post('/api/v1/members', {}).subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/members').flush(
      { title: 'Validation failed', status: 400, detail: 'Invalid request', errors: [{ field: 'fullName', message: 'must not be blank' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    expect(errors[0]).toBe('Invalid request (fullName: must not be blank)');
  });

  it('redirects to /settings on 401', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    http.get('/api/v1/documents').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/documents').flush({ title: 'Unauthorized', status: 401 }, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).toHaveBeenCalledWith(['/settings'], { queryParams: { reason: 'unauthorized' } });
    expect(errors.length).toBe(1);
  });

  it('stays quiet for silent requests', () => {
    const api = TestBed.inject(ApiService);
    api.getSystemHealth({ silent: true }).subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/system/health').flush(null, { status: 0, statusText: 'Unknown Error' });
    expect(errors).toEqual([]);
  });
});
