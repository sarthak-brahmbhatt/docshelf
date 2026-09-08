// 503: a background worker or subsystem is unavailable
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends DocshelfException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "unavailable", message);
    }
}
