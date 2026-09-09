// Derives the blob KEK, field KEK and HMAC key from DOCSHELF_MASTER_KEY with HKDF-SHA256; kekId identifies the master key generation
package com.docshelf.crypto;

import com.docshelf.config.DocshelfProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class KeyService {

    public static final String KEK_ID = "env-v1";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final int KEY_LEN = 32;

    private final SecretKey blobKek;
    private final SecretKey fieldKek;
    private final SecretKey hmacKey;

    @org.springframework.beans.factory.annotation.Autowired
    public KeyService(DocshelfProperties props) {
        this(decodeMasterKey(props.masterKey()));
    }

    KeyService(byte[] masterKey) {
        if (masterKey.length < KEY_LEN) {
            throw new IllegalStateException("DOCSHELF_MASTER_KEY must be at least 32 bytes (base64-encoded)");
        }
        byte[] prk = hkdfExtract(new byte[KEY_LEN], masterKey);
        this.blobKek = new SecretKeySpec(hkdfExpand(prk, "docshelf/blob-kek", KEY_LEN), "AES");
        this.fieldKek = new SecretKeySpec(hkdfExpand(prk, "docshelf/field-kek", KEY_LEN), "AES");
        this.hmacKey = new SecretKeySpec(hkdfExpand(prk, "docshelf/hmac-key", KEY_LEN), HMAC_ALG);
    }

    private static byte[] decodeMasterKey(String base64) {
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("DOCSHELF_MASTER_KEY is not valid base64");
        }
    }

    public String kekId() {
        return KEK_ID;
    }

    /** Key that wraps per-document blob DEKs (AES-KW). */
    public SecretKey blobKek(String kekId) {
        requireKnown(kekId);
        return blobKek;
    }

    /** Key for per-field AES-256-GCM encryption. */
    public SecretKey fieldKek() {
        return fieldKek;
    }

    /** Key for HMAC-SHA256 of normalised sensitive values (dedupe / exact lookup). */
    public SecretKey hmacKey() {
        return hmacKey;
    }

    private static void requireKnown(String kekId) {
        if (!KEK_ID.equals(kekId)) {
            throw new IllegalStateException("Unknown kek_id: " + kekId);
        }
    }

    // ---- HKDF (RFC 5869) with HMAC-SHA256 ----

    static byte[] hkdfExtract(byte[] salt, byte[] ikm) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(salt, HMAC_ALG));
            return mac.doFinal(ikm);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hkdfExpand(byte[] prk, String info, int length) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(prk, HMAC_ALG));
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[length];
            byte[] t = new byte[0];
            int pos = 0;
            for (int counter = 1; pos < length; counter++) {
                mac.reset();
                mac.update(t);
                mac.update(infoBytes);
                mac.update((byte) counter);
                t = mac.doFinal();
                int n = Math.min(t.length, length - pos);
                System.arraycopy(t, 0, out, pos, n);
                pos += n;
            }
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
