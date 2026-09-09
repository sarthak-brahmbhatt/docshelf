// Placeholder page for the chat feature; the feature agent replaces this component.
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '../../shared';

@Component({
  selector: 'app-chat-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <h1 class="page-title">Chat</h1>
    <app-empty-state icon="chat_bubble_outline" title="Coming soon" hint="Ask about your documents; confirm shares and reveals in-app." />
  `,
})
export class ChatPageComponent {}
