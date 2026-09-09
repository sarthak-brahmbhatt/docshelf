// Outcome of a WhatsApp send: provider message id and status (SIMULATED for the stub)
package com.docshelf.messaging;

import com.docshelf.messaging.entity.MessageProvider;
import com.docshelf.messaging.entity.MessageStatus;

public record WhatsAppSendResult(String providerMessageId, MessageStatus status, MessageProvider provider) {
}
