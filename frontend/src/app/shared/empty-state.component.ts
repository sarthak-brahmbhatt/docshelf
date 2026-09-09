// Centered icon + title + hint block for empty lists, with a content slot for an action button.
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <div class="empty">
      <mat-icon class="icon" aria-hidden="true">{{ icon() }}</mat-icon>
      <div class="title">{{ title() }}</div>
      @if (hint()) {
        <div class="hint">{{ hint() }}</div>
      }
      <div class="actions"><ng-content /></div>
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 8px;
      padding: 40px 16px;
      color: var(--mat-sys-on-surface-variant);
    }
    .icon { font-size: 56px; width: 56px; height: 56px; opacity: 0.6; }
    .title { font: var(--mat-sys-title-medium); color: var(--mat-sys-on-surface); }
    .hint { font: var(--mat-sys-body-medium); max-width: 40ch; }
    .actions:empty { display: none; }
    .actions { margin-top: 8px; }
  `,
})
export class EmptyStateComponent {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly hint = input<string>('');
}
