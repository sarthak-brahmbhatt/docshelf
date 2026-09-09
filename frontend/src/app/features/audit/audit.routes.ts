// Routes for the audit feature (lazy-loaded from app.routes.ts); the feature agent extends this file.
import { Routes } from '@angular/router';

export const AUDIT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./audit-page.component').then((m) => m.AuditPageComponent),
    title: 'DocShelf · Audit',
  },
];
