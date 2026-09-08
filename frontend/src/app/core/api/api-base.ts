// Resolves API URLs from the configured base URL and builds HttpParams from query objects (drops null/undefined, expands arrays).
import { HttpParams } from '@angular/common/http';

export const API_PREFIX = '/api/v1';

/** `http://host:8080` + `/documents` → `http://host:8080/api/v1/documents`; empty base → `/api/v1/documents`. */
export function apiUrl(baseUrl: string, path: string): string {
  const base = (baseUrl ?? '').replace(/\/+$/, '');
  const suffix = path.startsWith('/') ? path : `/${path}`;
  return `${base}${API_PREFIX}${suffix}`;
}

/** True when a request URL targets the DocShelf API (used by the auth interceptor). */
export function isApiRequest(url: string, baseUrl: string): boolean {
  if (url.startsWith(API_PREFIX) || url.startsWith('/actuator')) return true;
  const base = (baseUrl ?? '').replace(/\/+$/, '');
  return base !== '' && url.startsWith(`${base}/`);
}

type QueryValue = string | number | boolean | null | undefined | readonly (string | number | boolean)[];

/** Builds HttpParams, omitting null/undefined/empty-string values and repeating array params. */
export function toParams(query?: Record<string, QueryValue>): HttpParams {
  let params = new HttpParams();
  if (!query) return params;
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined || value === '') continue;
    if (Array.isArray(value)) {
      for (const item of value) {
        params = params.append(key, String(item));
      }
    } else {
      params = params.set(key, String(value));
    }
  }
  return params;
}
