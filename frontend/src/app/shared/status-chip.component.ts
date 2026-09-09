// Small coloured chip for document / reminder / job statuses, with a consistent tone mapping.
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type ChipTone = 'neutral' | 'info' | 'success' | 'warn' | 'error';

const TONES: Record<string, ChipTone> = {
  READY: 'success',
  SENT: 'success',
  DONE: 'success',
  EXECUTED: 'success',
  SIMULATED: 'success',
  NEEDS_REVIEW: 'warn',
  NEEDS_PASSWORD: 'warn',
  SNOOZED: 'warn',
  PENDING: 'info',
  QUEUED: 'info',
  RUNNING: 'info',
  SENDING: 'info',
  UPLOADED: 'info',
  EXTRACTING_TEXT: 'info',
  CLASSIFYING: 'info',
  EXTRACTING_FIELDS: 'info',
  INDEXING: 'info',
  FAILED: 'error',
  EXPIRED: 'error',
  CANCELLED: 'neutral',
  DISMISSED: 'neutral',
  SUPERSEDED: 'neutral',
  UNKNOWN: 'neutral',
};

const ICONS: Partial<Record<ChipTone, string>> = {
  success: 'check_circle',
  warn: 'warning',
  error: 'error',
  info: 'schedule',
};

@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <span class="chip" [class]="'chip tone-' + tone()">
      @if (showIcon() && icon()) {
        <mat-icon inline aria-hidden="true">{{ icon() }}</mat-icon>
      }
      <span>{{ label() }}</span>
    </span>
  `,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 10px;
      border-radius: 999px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
      line-height: 20px;
    }
    mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .tone-neutral { background: var(--mat-sys-surface-container-high); color: var(--mat-sys-on-surface-variant); }
    .tone-info { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .tone-success { background: #dcf5e3; color: #0b5b2a; }
    .tone-warn { background: #fff1cc; color: #6b4a00; }
    .tone-error { background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
  `,
})
export class StatusChipComponent {
  /** Status code, e.g. 'READY'. Rendered as a human label unless `label` is given. */
  readonly status = input.required<string>();
  /** Override the displayed text. */
  readonly text = input<string>('');
  /** Override the tone derived from the status. */
  readonly toneOverride = input<ChipTone | null>(null);
  readonly showIcon = input(true);

  protected readonly tone = computed<ChipTone>(() => this.toneOverride() ?? TONES[this.status()] ?? 'neutral');
  protected readonly icon = computed(() => ICONS[this.tone()]);
  protected readonly label = computed(() => this.text() || humanize(this.status()));
}

export function humanize(code: string): string {
  return (code ?? '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}
