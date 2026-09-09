// Placeholder page for the reminders feature; the feature agent replaces this component.
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-reminders-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Reminders</h1>
    <app-empty-state icon="notifications_none" title="Coming soon" hint="Upcoming expiries, premiums, follow-ups and refills; sync to your phone." />
  `,
})
export class RemindersPageComponent {}
