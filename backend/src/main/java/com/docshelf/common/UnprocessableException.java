// 422: syntactically valid but semantically unusable input (wrong PDF password, unparsable CSV)
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends DocshelfException {

    public UnprocessableException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "unprocessable", message);
    }

    public UnprocessableException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "unprocessable", message, cause);
    }
}
