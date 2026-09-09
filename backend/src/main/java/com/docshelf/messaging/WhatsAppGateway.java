// Transport abstraction for WhatsApp document delivery (Meta Cloud API shape); StubWhatsAppGateway is the v1 default
package com.docshelf.messaging;

import java.util.List;

public interface WhatsAppGateway {

    /**
     * Uploads the document and sends a template message referencing it.
     *
     * @param recipientE164 recipient number in E.164 form
     * @param templateName  approved template name
     * @param params        template body parameters in order
     * @param bytes         document bytes (already decrypted)
     * @param filename      filename shown to the recipient
     * @return provider message id and status
     */
    WhatsAppSendResult sendDocument(String recipientE164, String templateName, List<String> params, byte[] bytes,
                                    String filename);

    /** Sends a plain text message (reminders to the owner). */
    WhatsAppSendResult sendText(String recipientE164, String text);
}
