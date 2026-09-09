// Placeholder document detail page (/documents/:id); the document feature agent replaces this component.
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-document-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Document</h1>
    <app-empty-state
      icon="description"
      title="Coming soon"
      [hint]="'Fields, original, reminders and audit for document ' + id()"
    />
  `,
})
export class DocumentPageComponent {
  /** Bound from the `:id` route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();
}
