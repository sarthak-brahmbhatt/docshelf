// Placeholder page for the shelf feature; the feature agent replaces this component.
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-shelf-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Shelf</h1>
    <app-empty-state icon="folder_open" title="Coming soon" hint="Your documents: upload, capture, type or record, then browse by member and type." />
  `,
})
export class ShelfPageComponent {}
