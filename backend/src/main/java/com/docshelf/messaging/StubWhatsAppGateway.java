// v1 WhatsApp gateway: no network, records what would have been sent and reports status SIMULATED
package com.docshelf.messaging;

import com.docshelf.messaging.entity.MessageProvider;
import com.docshelf.messaging.entity.MessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "docshelf.whatsapp", name = "provider", havingValue = "STUB", matchIfMissing = true)
public class StubWhatsAppGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(StubWhatsAppGateway.class);

    /** What the stub recorded (never the bytes): recipient, template, filename, size, time. */
    public record Simulated(String providerMessageId, String recipientE164, String templateName, List<String> params,
                            String filename, int sizeBytes, String text, Instant at) {
    }

    private final Clock clock;
    private final List<Simulated> sent = Collections.synchronizedList(new ArrayList<>());

    public StubWhatsAppGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public WhatsAppSendResult sendDocument(String recipientE164, String templateName, List<String> params,
                                           byte[] bytes, String filename) {
        String id = "SIMULATED-" + UUID.randomUUID();
        sent.add(new Simulated(id, recipientE164, templateName, params == null ? List.of() : List.copyOf(params),
                filename, bytes == null ? 0 : bytes.length, null, clock.instant()));
        log.info("WhatsApp STUB: simulated document send template={} filename={} bytes={} id={}",
                templateName, filename, bytes == null ? 0 : bytes.length, id);
        return new WhatsAppSendResult(id, MessageStatus.SIMULATED, MessageProvider.STUB);
    }

    @Override
    public WhatsAppSendResult sendText(String recipientE164, String text) {
        String id = "SIMULATED-" + UUID.randomUUID();
        sent.add(new Simulated(id, recipientE164, null, List.of(), null, 0, text, clock.instant()));
        log.info("WhatsApp STUB: simulated text send id={}", id);
        return new WhatsAppSendResult(id, MessageStatus.SIMULATED, MessageProvider.STUB);
    }

    public List<Simulated> simulated() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
