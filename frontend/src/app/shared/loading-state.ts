// LoadingState<T> union used by every page (idle | loading | ready | error) plus small helpers to build and unwrap it.
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError, map, startWith } from 'rxjs/operators';
import { of } from 'rxjs';
import { describeHttpError } from '../core/api/error.interceptor';

export type LoadingState<T> =
  | { status: 'idle'; data?: undefined; error?: undefined }
  | { status: 'loading'; data?: T; error?: undefined }
  | { status: 'ready'; data: T; error?: undefined }
  | { status: 'error'; data?: T; error: string };

export const idle = <T>(): LoadingState<T> => ({ status: 'idle' });

/** Loading; pass the previous data to keep showing it while refreshing. */
export const loading = <T>(previous?: T): LoadingState<T> => ({ status: 'loading', data: previous });

export const ready = <T>(data: T): LoadingState<T> => ({ status: 'ready', data });

export const failed = <T>(error: unknown, previous?: T): LoadingState<T> => ({
  status: 'error',
  error: errorMessage(error),
  data: previous,
});

export function isReady<T>(state: LoadingState<T>): state is { status: 'ready'; data: T } {
  return state.status === 'ready';
}

export function isLoading<T>(state: LoadingState<T>): boolean {
  return state.status === 'loading';
}

/** Converts any thrown value into a display string (ProblemDetail-aware). */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) return describeHttpError(error);
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return 'Something went wrong';
}

/** Wraps an observable so it emits loading → ready | error (never errors itself); handy with toSignal(). */
export function toLoadingState<T>(source: Observable<T>, previous?: T): Observable<LoadingState<T>> {
  return source.pipe(
    map((data) => ready(data)),
    catchError((err: unknown) => of(failed<T>(err, previous))),
    startWith(loading<T>(previous)),
  );
}
