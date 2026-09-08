// HTTP interceptor that attaches `Authorization: Bearer <token>` to every DocShelf API request.
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SettingsService } from '../settings/settings.service';
import { isApiRequest } from './api-base';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const settings = inject(SettingsService);
  const token = settings.apiToken();
  if (!token || !isApiRequest(req.url, settings.apiBaseUrl()) || req.headers.has('Authorization')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
