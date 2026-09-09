// Routes for the family feature (lazy-loaded from app.routes.ts); the feature agent extends this file.
import { Routes } from '@angular/router';

export const FAMILY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./family-page.component').then((m) => m.FamilyPageComponent),
    title: 'DocShelf · Family',
  },
];
