// Top-level routes: every feature is lazy-loaded from features/<name>/<name>.routes.ts.
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'shelf' },
  { path: 'shelf', loadChildren: () => import('./features/shelf/shelf.routes').then((m) => m.SHELF_ROUTES) },
  {
    path: 'documents',
    loadChildren: () => import('./features/document/document.routes').then((m) => m.DOCUMENT_ROUTES),
  },
  { path: 'family', loadChildren: () => import('./features/family/family.routes').then((m) => m.FAMILY_ROUTES) },
  {
    path: 'reminders',
    loadChildren: () => import('./features/reminders/reminders.routes').then((m) => m.REMINDERS_ROUTES),
  },
  {
    path: 'portfolio',
    loadChildren: () => import('./features/portfolio/portfolio.routes').then((m) => m.PORTFOLIO_ROUTES),
  },
  { path: 'chat', loadChildren: () => import('./features/chat/chat.routes').then((m) => m.CHAT_ROUTES) },
  { path: 'audit', loadChildren: () => import('./features/audit/audit.routes').then((m) => m.AUDIT_ROUTES) },
  {
    path: 'settings',
    loadChildren: () => import('./features/settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
  },
  { path: '**', redirectTo: 'shelf' },
];
