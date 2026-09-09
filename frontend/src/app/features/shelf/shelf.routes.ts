// Routes for the shelf feature (lazy-loaded from app.routes.ts); the feature agent extends this file.
import { Routes } from '@angular/router';

export const SHELF_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./shelf-page.component').then((m) => m.ShelfPageComponent),
    title: 'DocShelf · Shelf',
  },
];
