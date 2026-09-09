// Transport abstraction for email; B5 implements SmtpEmailGateway on JavaMailSender, tests use FakeEmailGateway
package com.docshelf.messaging;

public interface EmailGateway {

    /** Sends synchronously; throws UpstreamException on transport failure. */
    EmailSendResult send(EmailMessage message);
}
