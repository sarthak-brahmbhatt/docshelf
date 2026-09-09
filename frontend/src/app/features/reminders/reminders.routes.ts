// Routes for the reminders feature (lazy-loaded from app.routes.ts); the feature agent extends this file.
import { Routes } from '@angular/router';

export const REMINDERS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./reminders-page.component').then((m) => m.RemindersPageComponent),
    title: 'DocShelf · Reminders',
  },
];
