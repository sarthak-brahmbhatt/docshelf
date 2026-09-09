// Routes for the chat feature (lazy-loaded from app.routes.ts); the feature agent extends this file.
import { Routes } from '@angular/router';

export const CHAT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./chat-page.component').then((m) => m.ChatPageComponent),
    title: 'DocShelf · Chat',
  },
];
