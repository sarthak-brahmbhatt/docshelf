// Outbound email description handed to EmailGateway (attachment bytes are decrypted in memory only for the send)
package com.docshelf.messaging;

import java.util.List;

public record EmailMessage(
        String to,
        String subject,
        String textBody,
        String htmlBody,
        List<Attachment> attachments,
        String dedupeKey) {

    public record Attachment(String filename, String contentType, byte[] bytes) {
    }

    public EmailMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static EmailMessage text(String to, String subject, String textBody, String dedupeKey) {
        return new EmailMessage(to, subject, textBody, null, List.of(), dedupeKey);
    }

    /** RFC 5322 Message-ID derived from the dedupe key so retries are idempotent at the mail server. */
    public String messageId() {
        return "<" + dedupeKey + "@docshelf.local>";
    }
}
