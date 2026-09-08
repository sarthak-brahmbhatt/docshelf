// 409: duplicate upload, illegal state transition, referenced entity in use
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class ConflictException extends DocshelfException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "conflict", message);
    }
}
