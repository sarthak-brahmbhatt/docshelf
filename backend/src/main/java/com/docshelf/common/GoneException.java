// 410: the resource existed but has expired (e.g. a pending chat action)
package com.docshelf.common;

import org.springframework.http.HttpStatus;

public class GoneException extends DocshelfException {

    public GoneException(String message) {
        super(HttpStatus.GONE, "gone", message);
    }
}
