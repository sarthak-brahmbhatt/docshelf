// Grey shimmering placeholder blocks shown while a page's LoadingState is 'loading'.
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="skeleton-list" role="status" aria-live="polite" aria-label="Loading">
      @for (row of rows(); track row) {
        <div class="skeleton-row" [style.height]="height()" [style.width]="row"></div>
      }
    </div>
  `,
  styles: `
    .skeleton-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .skeleton-row {
      border-radius: 8px;
      background: linear-gradient(
        90deg,
        var(--mat-sys-surface-container) 25%,
        var(--mat-sys-surface-container-high) 50%,
        var(--mat-sys-surface-container) 75%
      );
      background-size: 200% 100%;
      animation: shimmer 1.4s infinite;
    }
    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
    @media (prefers-reduced-motion: reduce) {
      .skeleton-row { animation: none; }
    }
  `,
})
export class SkeletonComponent {
  /** Number of placeholder rows. */
  readonly lines = input(3);
  /** Row height (CSS length). */
  readonly height = input('20px');

  protected readonly rows = computed(() => {
    const widths = ['100%', '85%', '92%', '70%', '95%', '60%'];
    return Array.from({ length: this.lines() }, (_, i) => widths[i % widths.length]);
  });
}
