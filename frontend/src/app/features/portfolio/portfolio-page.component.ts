// Placeholder page for the portfolio feature; the feature agent replaces this component.
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-portfolio-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Portfolio</h1>
    <app-empty-state icon="show_chart" title="Coming soon" hint="Mutual fund holdings, XIRR and underperformers from your CAS." />
  `,
})
export class PortfolioPageComponent {}
