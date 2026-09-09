// HMAC-SHA256 (hex) of normalised sensitive values for duplicate detection and exact lookup without decrypting
package com.docshelf.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;
import javax.crypto.Mac;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Service;

@Service
public class Hmac {

    private final KeyService keys;

    public Hmac(KeyService keys) {
        this.keys = keys;
    }

    /** Lower-case hex HMAC of the value after {@link #normalize(String)}; null for null/blank input. */
    public String hmacHex(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keys.hmacKey());
            return Hex.encodeHexString(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("HMAC failed", e);
        }
    }

    /** Upper-cases and strips whitespace, hyphens and dots so equal identifiers hash equally. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String n = value.replaceAll("[\\s\\-.]", "").toUpperCase(Locale.ROOT);
        return n.isEmpty() ? null : n;
    }
}
