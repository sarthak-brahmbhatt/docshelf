// Routes for the document detail feature (mounted at /documents); the feature agent extends this file.
import { Routes } from '@angular/router';

export const DOCUMENT_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: '/shelf' },
  {
    path: ':id',
    loadComponent: () => import('./document-page.component').then((m) => m.DocumentPageComponent),
    title: 'DocShelf · Document',
  },
];
