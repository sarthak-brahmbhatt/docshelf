// Routes for the portfolio feature (lazy-loaded from app.routes.ts); the feature agent extends this file.
import { Routes } from '@angular/router';

export const PORTFOLIO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./portfolio-page.component').then((m) => m.PortfolioPageComponent),
    title: 'DocShelf · Portfolio',
  },
];
