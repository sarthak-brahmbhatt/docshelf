// EmailGateway test double capturing every message for assertions
package com.docshelf.testsupport;

import com.docshelf.messaging.EmailGateway;
import com.docshelf.messaging.EmailMessage;
import com.docshelf.messaging.EmailSendResult;
import com.docshelf.messaging.entity.MessageStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeEmailGateway implements EmailGateway {

    private final List<EmailMessage> sent = Collections.synchronizedList(new ArrayList<>());
    private volatile RuntimeException failure;

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (failure != null) {
            throw failure;
        }
        sent.add(message);
        return new EmailSendResult(message.messageId(), MessageStatus.SENT);
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }

    /** Makes subsequent sends throw (to test failure paths); pass null to clear. */
    public void failWith(RuntimeException e) {
        this.failure = e;
    }

    public void reset() {
        sent.clear();
        failure = null;
    }
}
