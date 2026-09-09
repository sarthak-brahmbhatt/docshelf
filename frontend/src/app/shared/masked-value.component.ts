// Shows a masked sensitive value with a Reveal button; the passed-in reveal fn returns the full value, shown for 30 s with copy, then re-masked.
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, firstValueFrom, isObservable } from 'rxjs';
import { errorMessage } from './loading-state';

/** Result of a reveal call: either the raw value or a RevealResponse-like object carrying `value` and `displaySeconds`. */
export type RevealResult = string | { value: string; displaySeconds?: number };
export type RevealFn = () => Promise<RevealResult> | Observable<RevealResult>;

@Component({
  selector: 'app-masked-value',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  template: `
    <span class="masked-value" [class.revealed]="revealed()">
      <code class="value" [attr.aria-label]="revealed() ? 'Revealed value' : 'Masked value'">{{
        revealed() ? value() : masked() || '—'
      }}</code>

      @if (busy()) {
        <mat-spinner diameter="18" aria-label="Revealing"></mat-spinner>
      } @else if (revealed()) {
        <span class="countdown" aria-live="polite">{{ secondsLeft() }}s</span>
        <button mat-icon-button type="button" matTooltip="Copy" aria-label="Copy value" (click)="copy()">
          <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>
        </button>
        <button mat-icon-button type="button" matTooltip="Hide" aria-label="Hide value" (click)="hide()">
          <mat-icon>visibility_off</mat-icon>
        </button>
      } @else if (reveal()) {
        <button
          mat-icon-button
          type="button"
          [matTooltip]="'Reveal ' + (label() || 'value') + ' (audited)'"
          [attr.aria-label]="'Reveal ' + (label() || 'value')"
          [disabled]="disabled()"
          (click)="show()"
        >
          <mat-icon>visibility</mat-icon>
        </button>
      }
      @if (error()) {
        <span class="error" role="alert">{{ error() }}</span>
      }
    </span>
  `,
  styles: `
    .masked-value { display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; }
    .value {
      font-family: 'Roboto Mono', ui-monospace, monospace;
      letter-spacing: 0.04em;
      padding: 2px 6px;
      border-radius: 6px;
      background: var(--mat-sys-surface-container);
    }
    .revealed .value { background: var(--mat-sys-tertiary-container); color: var(--mat-sys-on-tertiary-container); }
    .countdown { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); min-width: 2.5ch; }
    .error { font: var(--mat-sys-label-small); color: var(--mat-sys-error); }
  `,
})
export class MaskedValueComponent {
  private readonly destroyRef = inject(DestroyRef);

  /** Masked display value, e.g. `XXXX XXXX 1234`. */
  readonly masked = input.required<string | null | undefined>();
  /** Human label used in the tooltip / aria label. */
  readonly label = input<string>('');
  /** Called when the user presses Reveal; typically `() => api.revealField(id, 'number')`. Omit to hide the button. */
  readonly reveal = input<RevealFn | null>(null);
  readonly disabled = input(false);
  /** Seconds to keep the value visible when the reveal result does not specify `displaySeconds`. */
  readonly displaySeconds = input(30);

  readonly revealed = signal(false);
  readonly value = signal('');
  readonly secondsLeft = signal(0);
  readonly busy = signal(false);
  readonly copied = signal(false);
  readonly error = signal<string | null>(null);

  private timer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearTimer());
  }

  async show(): Promise<void> {
    const fn = this.reveal();
    if (!fn || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      const raw = fn();
      const result = isObservable(raw) ? await firstValueFrom(raw) : await raw;
      const value = typeof result === 'string' ? result : result.value;
      const seconds = typeof result === 'string' ? this.displaySeconds() : (result.displaySeconds ?? this.displaySeconds());
      this.value.set(value);
      this.revealed.set(true);
      this.startCountdown(Math.max(1, Math.floor(seconds)));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  hide(): void {
    this.clearTimer();
    this.revealed.set(false);
    this.value.set('');
    this.secondsLeft.set(0);
    this.copied.set(false);
  }

  async copy(): Promise<void> {
    try {
      await navigator.clipboard?.writeText(this.value());
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    } catch {
      this.error.set('Copy failed');
    }
  }

  private startCountdown(seconds: number): void {
    this.clearTimer();
    this.secondsLeft.set(seconds);
    this.timer = setInterval(() => {
      const next = this.secondsLeft() - 1;
      if (next <= 0) {
        this.hide();
      } else {
        this.secondsLeft.set(next);
      }
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
