// Application chrome: toolbar + side navigation rail on desktop, bottom navigation on phones, with the router outlet in the middle.
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs/operators';
import { SettingsService } from '../core/settings/settings.service';

export interface NavItem {
  path: string;
  label: string;
  icon: string;
  /** Shown in the phone bottom bar (max 5). Others are reachable from the drawer. */
  bottom: boolean;
}

export const NAV_ITEMS: readonly NavItem[] = [
  { path: '/shelf', label: 'Shelf', icon: 'folder_open', bottom: true },
  { path: '/chat', label: 'Chat', icon: 'chat_bubble_outline', bottom: true },
  { path: '/reminders', label: 'Reminders', icon: 'notifications_none', bottom: true },
  { path: '/family', label: 'Family', icon: 'people_outline', bottom: true },
  { path: '/portfolio', label: 'Portfolio', icon: 'show_chart', bottom: false },
  { path: '/audit', label: 'Audit', icon: 'history', bottom: false },
  { path: '/settings', label: 'Settings', icon: 'settings', bottom: false },
] as const;

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly router = inject(Router);
  protected readonly settings = inject(SettingsService);

  protected readonly navItems = NAV_ITEMS;
  protected readonly bottomItems = NAV_ITEMS.filter((i) => i.bottom);

  /** Phone layout: no rail, bottom bar + over-mode drawer. */
  protected readonly isHandset = toSignal(
    this.breakpoints.observe([Breakpoints.Handset, Breakpoints.TabletPortrait]).pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  protected readonly drawerOpen = signal(false);

  protected readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly pageTitle = computed(() => {
    const url = this.currentUrl();
    if (url.startsWith('/documents/')) return 'Document';
    return NAV_ITEMS.find((i) => url.startsWith(i.path))?.label ?? 'DocShelf';
  });

  /** Items not in the bottom bar; shown in the drawer's "More" section on phones. */
  protected readonly drawerItems = computed(() => (this.isHandset() ? NAV_ITEMS.filter((i) => !i.bottom) : NAV_ITEMS));

  protected readonly showSetupHint = computed(() => this.settings.loaded() && (this.settings.needsBaseUrl() || !this.settings.hasToken()));

  toggleDrawer(): void {
    this.drawerOpen.update((v) => !v);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }
}
