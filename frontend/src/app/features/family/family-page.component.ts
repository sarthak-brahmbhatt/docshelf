// Placeholder page for the family feature; the feature agent replaces this component.
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-family-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Family</h1>
    <app-empty-state icon="people_outline" title="Coming soon" hint="Family members and share contacts." />
  `,
})
export class FamilyPageComponent {}
