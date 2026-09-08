// 502: an external dependency (OpenAI, AMFI, SMTP, Meta) failed or is not configured
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class UpstreamException extends DocshelfException {

    private final boolean retryable;

    public UpstreamException(String message) {
        this(message, null, false);
    }

    public UpstreamException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public UpstreamException(String message, Throwable cause, boolean retryable) {
        super(HttpStatus.BAD_GATEWAY, "upstream", message, cause);
        this.retryable = retryable;
    }

    /** True for transient upstream failures (429 / 5xx / IO) that a worker may retry. */
    public boolean retryable() {
        return retryable;
    }
}
