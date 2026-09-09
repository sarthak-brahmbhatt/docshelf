// AES-256-GCM encryption of individual sensitive fields; ciphertext layout v1 | iv(12) | ct | tag(16), AAD = table.column:rowId
package com.docshelf.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Service;

@Service
public class FieldCipher {

    static final byte[] VERSION = "v1".getBytes(StandardCharsets.US_ASCII);
    static final int IV_LEN = 12;
    static final int TAG_BITS = 128;

    private final KeyService keys;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(KeyService keys) {
        this.keys = keys;
    }

    /** Builds the canonical AAD string {@code table.column:rowId}. */
    public static String aad(String table, String column, Object rowId) {
        return table + "." + column + ":" + rowId;
    }

    public byte[] encrypt(String plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), aad);
    }

    public byte[] encrypt(byte[] plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.fieldKek(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[VERSION.length + IV_LEN + ct.length];
            System.arraycopy(VERSION, 0, out, 0, VERSION.length);
            System.arraycopy(iv, 0, out, VERSION.length, IV_LEN);
            System.arraycopy(ct, 0, out, VERSION.length + IV_LEN, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Field encryption failed", e);
        }
    }

    public String decryptToString(byte[] blob, String aad) {
        byte[] plain = decrypt(blob, aad);
        if (plain == null) {
            return null;
        }
        try {
            return new String(plain, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public byte[] decrypt(byte[] blob, String aad) {
        if (blob == null) {
            return null;
        }
        if (blob.length < VERSION.length + IV_LEN + TAG_BITS / 8
                || blob[0] != VERSION[0] || blob[1] != VERSION[1]) {
            throw new CryptoException("Unrecognised field ciphertext format");
        }
        try {
            byte[] iv = Arrays.copyOfRange(blob, VERSION.length, VERSION.length + IV_LEN);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.fieldKek(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(blob, VERSION.length + IV_LEN, blob.length - VERSION.length - IV_LEN);
        } catch (AEADBadTagException e) {
            throw new CryptoException("Field ciphertext authentication failed (tampered or wrong AAD)", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Field decryption failed", e);
        }
    }
}
