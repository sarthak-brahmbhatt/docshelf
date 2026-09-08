// Base class for API-level exceptions that map to an HTTP status and an RFC 9457 problem type slug
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public abstract class DocshelfException extends RuntimeException {

    private final HttpStatus status;
    private final String typeSlug;

    protected DocshelfException(HttpStatus status, String typeSlug, String message) {
        super(message);
        this.status = status;
        this.typeSlug = typeSlug;
    }

    protected DocshelfException(HttpStatus status, String typeSlug, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.typeSlug = typeSlug;
    }

    public HttpStatus status() {
        return status;
    }

    public String typeSlug() {
        return typeSlug;
    }
}
