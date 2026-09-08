// HTTP interceptor that maps RFC 9457 ProblemDetail errors to a snackbar message and redirects to /settings on 401.
import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ToastService } from '../ui/toast.service';
import { ProblemDetail } from './models';

/** Set on a request's HttpContext to suppress the global error snackbar (caller handles the error itself). */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const silent = req.context.get(SILENT_ERRORS);
        if (err.status === 401 || err.status === 403) {
          if (!silent) toast.error('Not authorised — check the API token in Settings.');
          if (!router.url.startsWith('/settings')) {
            void router.navigate(['/settings'], { queryParams: { reason: 'unauthorized' } });
          }
        } else if (!silent) {
          toast.error(describeHttpError(err));
        }
      }
      return throwError(() => err);
    }),
  );
};

/** Extracts the ProblemDetail body from an HttpErrorResponse, if the server sent one. */
export function problemOf(err: unknown): ProblemDetail | null {
  if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' && 'title' in err.error) {
    return err.error as ProblemDetail;
  }
  return null;
}

/** Human-readable message for any HTTP error, preferring the ProblemDetail `detail`. */
export function describeHttpError(err: HttpErrorResponse): string {
  const problem = problemOf(err);
  if (problem) {
    const validation = problem.errors?.length
      ? ` (${problem.errors.map((e) => `${e.field}: ${e.message}`).join('; ')})`
      : '';
    return `${problem.detail || problem.title}${validation}`;
  }
  if (err.status === 0) {
    return 'Cannot reach the DocShelf server — check the API URL in Settings and your network.';
  }
  if (err.status === 413) return 'File is too large for the server.';
  if (err.status === 415) return 'Unsupported file type.';
  if (err.status === 502) return 'Upstream service failed (OpenAI, AMFI, mail or WhatsApp).';
  if (err.status === 503) return 'Server is busy — try again shortly.';
  return `Request failed (${err.status}${err.statusText ? ' ' + err.statusText : ''}).`;
}
