// Placeholder page for the audit feature; the feature agent replaces this component.
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-audit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Audit</h1>
    <app-empty-state icon="history" title="Coming soon" hint="Every reveal, share, download and LLM call, append-only." />
  `,
})
export class AuditPageComponent {}
