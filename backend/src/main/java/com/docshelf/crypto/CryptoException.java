// Unchecked wrapper for cryptographic failures (never carries key or plaintext material in its message)
package com.docshelf.crypto;

public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
