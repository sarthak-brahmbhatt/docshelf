// 400: request is malformed in a way bean validation does not catch (e.g. field is not sensitive)
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class BadRequestException extends DocshelfException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "bad-request", message);
    }
}
