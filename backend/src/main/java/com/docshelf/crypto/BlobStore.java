// Encrypted file store: per-document random DEK (AES-256-GCM, AAD = document id) wrapped with AES-KW under the blob KEK; layout DSH1|kekId|iv|ct|tag
package com.docshelf.crypto;

import com.docshelf.config.DocshelfProperties;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class BlobStore {

    static final byte[] MAGIC = "DSH1".getBytes(StandardCharsets.US_ASCII);
    static final int IV_LEN = 12;
    static final int TAG_BITS = 128;
    static final int DEK_LEN = 32;

    private final KeyService keys;
    private final Clock clock;
    private final Path root;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public BlobStore(KeyService keys, DocshelfProperties props, Clock clock) {
        this(keys, Path.of(props.blobDir()), clock);
    }

    BlobStore(KeyService keys, Path root, Clock clock) {
        this.keys = keys;
        this.clock = clock;
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create blob directory " + this.root, e);
        }
    }

    public Path root() {
        return root;
    }

    /** Encrypts and writes plaintext under blob-dir/yyyy/MM/&lt;uuid&gt;.bin with a fresh DEK; returns the relative path and wrapped DEK. */
    public StoredBlob store(byte[] plaintext, UUID documentId) {
        byte[] dek = new byte[DEK_LEN];
        random.nextBytes(dek);
        try {
            return write(plaintext, documentId, dek, keys.kekId());
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * Writes a second blob (e.g. the unlocked copy of a password-protected PDF) under the SAME DEK as an existing
     * blob, so one document.dek_wrapped covers both storage_path and unlocked_storage_path.
     */
    public StoredBlob storeWithExistingDek(byte[] plaintext, UUID documentId, byte[] dekWrapped, String kekId) {
        byte[] dek = null;
        try {
            dek = unwrap(dekWrapped, kekId);
            return write(plaintext, documentId, dek, kekId);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("DEK unwrap failed", e);
        } finally {
            if (dek != null) {
                Arrays.fill(dek, (byte) 0);
            }
        }
    }

    private StoredBlob write(byte[] plaintext, UUID documentId, byte[] dek, String kekId) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(documentId));
            byte[] ct = cipher.doFinal(plaintext);

            byte[] kekIdBytes = kekId.getBytes(StandardCharsets.UTF_8);
            String relative = relativePath();
            Path target = resolve(relative);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream os = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(os)) {
                out.write(MAGIC);
                out.writeByte(kekIdBytes.length);
                out.write(kekIdBytes);
                out.write(iv);
                out.write(ct); // GCM output = ciphertext || tag
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            byte[] wrapped = wrap(dek, kekId);
            return new StoredBlob(relative, wrapped, kekId, plaintext.length, Files.size(target));
        } catch (GeneralSecurityException | IOException e) {
            throw new CryptoException("Blob store failed", e);
        }
    }

    /** Reads and decrypts a blob into memory. Callers should zero the returned buffer when done. */
    public byte[] load(String storagePath, byte[] dekWrapped, String kekId, UUID documentId) {
        Path file = resolve(storagePath);
        byte[] dek = null;
        try (InputStream is = Files.newInputStream(file); DataInputStream in = new DataInputStream(is)) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new CryptoException("Not a DocShelf blob: " + storagePath);
            }
            int kekLen = in.readUnsignedByte();
            String fileKekId = new String(in.readNBytes(kekLen), StandardCharsets.UTF_8);
            if (!fileKekId.equals(kekId)) {
                throw new CryptoException("kek_id mismatch for blob " + storagePath);
            }
            byte[] iv = in.readNBytes(IV_LEN);
            byte[] ct = in.readAllBytes();
            dek = unwrap(dekWrapped, kekId);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(documentId));
            return cipher.doFinal(ct);
        } catch (AEADBadTagException e) {
            throw new CryptoException("Blob authentication failed (tampered, wrong DEK or wrong document id)", e);
        } catch (GeneralSecurityException | IOException e) {
            throw new CryptoException("Blob load failed", e);
        } finally {
            if (dek != null) {
                Arrays.fill(dek, (byte) 0);
            }
        }
    }

    /** Decrypts a blob to an output stream (for streaming downloads). */
    public void copyTo(String storagePath, byte[] dekWrapped, String kekId, UUID documentId, OutputStream out) {
        byte[] plain = load(storagePath, dekWrapped, kekId, documentId);
        try {
            out.write(plain);
            out.flush();
        } catch (IOException e) {
            throw new CryptoException("Blob copy failed", e);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public boolean exists(String storagePath) {
        return Files.isRegularFile(resolve(storagePath));
    }

    public void delete(String storagePath) {
        if (storagePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            throw new CryptoException("Blob delete failed", e);
        }
    }

    /** Wraps a DEK with AES-KW (RFC 3394) under the blob KEK. */
    byte[] wrap(byte[] dek, String kekId) throws GeneralSecurityException {
        Cipher kw = Cipher.getInstance("AESWrap");
        kw.init(Cipher.WRAP_MODE, keys.blobKek(kekId));
        return kw.wrap(new SecretKeySpec(dek, "AES"));
    }

    byte[] unwrap(byte[] wrapped, String kekId) throws GeneralSecurityException {
        Cipher kw = Cipher.getInstance("AESWrap");
        kw.init(Cipher.UNWRAP_MODE, keys.blobKek(kekId));
        SecretKey key = (SecretKey) kw.unwrap(wrapped, "AES", Cipher.SECRET_KEY);
        return key.getEncoded();
    }

    private static byte[] aad(UUID documentId) {
        return documentId.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String relativePath() {
        LocalDate today = LocalDate.now(clock);
        return String.format("%04d/%02d/%s.bin", today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }

    private Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw new CryptoException("Blob path escapes the blob directory");
        }
        return p;
    }

    /** Helper for tests and tooling: reads raw stored bytes. */
    byte[] rawBytes(String storagePath) throws IOException {
        try (InputStream is = Files.newInputStream(resolve(storagePath)); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            is.transferTo(bos);
            return bos.toByteArray();
        }
    }
}
