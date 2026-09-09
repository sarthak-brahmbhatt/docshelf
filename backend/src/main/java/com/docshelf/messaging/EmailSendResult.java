// Outcome of an email send: provider message id (the RFC Message-ID) and resulting status
package com.docshelf.messaging;

import com.docshelf.messaging.entity.MessageStatus;

public record EmailSendResult(String providerMessageId, MessageStatus status) {
}
