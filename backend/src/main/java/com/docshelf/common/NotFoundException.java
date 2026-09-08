// 404: the requested entity does not exist
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DocshelfException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not-found", message);
    }

    public NotFoundException(String entity, Object id) {
        this(entity + " " + id + " not found");
    }
}
