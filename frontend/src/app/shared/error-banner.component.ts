// Inline error banner with an optional Retry button, shown when a page's LoadingState is 'error'.
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-error-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="banner" role="alert">
      <mat-icon aria-hidden="true">error_outline</mat-icon>
      <div class="text">
        @if (title()) {
          <div class="title">{{ title() }}</div>
        }
        <div class="message">{{ message() }}</div>
      </div>
      @if (retryable()) {
        <button mat-stroked-button type="button" (click)="retry.emit()">
          <mat-icon>refresh</mat-icon>
          Retry
        </button>
      }
    </div>
  `,
  styles: `
    .banner {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .text { flex: 1; min-width: 0; }
    .title { font: var(--mat-sys-title-small); }
    .message { font: var(--mat-sys-body-medium); overflow-wrap: anywhere; }
  `,
})
export class ErrorBannerComponent {
  readonly message = input.required<string>();
  readonly title = input<string>('');
  /** Show a Retry button that emits `retry`. */
  readonly retryable = input(true);
  readonly retry = output<void>();
}
