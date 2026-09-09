// BlobStore: encrypted round trip, on-disk layout, AAD binding to the document id, shared-DEK second blob, delete
package com.docshelf.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlobStoreTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private BlobStore store() {
        return new BlobStore(TestKeys.keyService(), dir, clock);
    }

    @Test
    void roundTripAndLayout() throws Exception {
        BlobStore store = store();
        UUID docId = UUID.randomUUID();
        byte[] plain = "%PDF-1.7 hello world".getBytes(StandardCharsets.UTF_8);
        StoredBlob stored = store.store(plain, docId);

        assertThat(stored.storagePath()).matches("2026/09/[0-9a-f-]{36}\\.bin");
        assertThat(stored.kekId()).isEqualTo("env-v1");
        assertThat(stored.dekWrapped()).hasSize(40); // AES-KW of a 32-byte key
        assertThat(Files.exists(dir.resolve(stored.storagePath()))).isTrue();

        byte[] raw = store.rawBytes(stored.storagePath());
        assertThat(new String(raw, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("DSH1");
        assertThat(raw[4]).isEqualTo((byte) 6);
        assertThat(new String(raw, 5, 6, StandardCharsets.UTF_8)).isEqualTo("env-v1");
        assertThat(raw.length).isEqualTo(4 + 1 + 6 + 12 + plain.length + 16);
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("hello world");

        assertThat(store.load(stored.storagePath(), stored.dekWrapped(), stored.kekId(), docId)).isEqualTo(plain);
    }

    @Test
    void boundToDocumentIdAndDek() {
        BlobStore store = store();
        UUID docId = UUID.randomUUID();
        StoredBlob a = store.store("A".getBytes(), docId);
        StoredBlob b = store.store("B".getBytes(), UUID.randomUUID());
        assertThatThrownBy(() -> store.load(a.storagePath(), a.dekWrapped(), a.kekId(), UUID.randomUUID()))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> store.load(a.storagePath(), b.dekWrapped(), a.kekId(), docId))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> store.load(a.storagePath(), a.dekWrapped(), "env-v9", docId))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void secondBlobUnderSameDek() {
        BlobStore store = store();
        UUID docId = UUID.randomUUID();
        StoredBlob locked = store.store("locked".getBytes(), docId);
        StoredBlob unlocked = store.storeWithExistingDek("unlocked".getBytes(), docId, locked.dekWrapped(), locked.kekId());
        assertThat(unlocked.storagePath()).isNotEqualTo(locked.storagePath());
        assertThat(store.load(unlocked.storagePath(), locked.dekWrapped(), locked.kekId(), docId)).isEqualTo("unlocked".getBytes());
    }

    @Test
    void deleteAndPathSafety() {
        BlobStore store = store();
        StoredBlob s = store.store(new byte[0], UUID.randomUUID());
        assertThat(store.exists(s.storagePath())).isTrue();
        store.delete(s.storagePath());
        assertThat(store.exists(s.storagePath())).isFalse();
        assertThatThrownBy(() -> store.exists("../../etc/passwd")).isInstanceOf(CryptoException.class);
    }
}
