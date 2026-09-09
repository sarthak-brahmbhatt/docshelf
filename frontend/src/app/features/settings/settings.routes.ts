// Routes for the settings feature (lazy-loaded from app.routes.ts).
import { Routes } from '@angular/router';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./settings-page.component').then((m) => m.SettingsPageComponent),
    title: 'DocShelf · Settings',
  },
];
