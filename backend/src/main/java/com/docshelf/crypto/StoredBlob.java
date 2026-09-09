// Result of writing an encrypted blob: what the document row must persist to be able to read it back
package com.docshelf.crypto;

public record StoredBlob(String storagePath, byte[] dekWrapped, String kekId, long plaintextSize, long storedSize) {
}
