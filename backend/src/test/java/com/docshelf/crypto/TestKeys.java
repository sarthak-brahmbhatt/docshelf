// Shared dev master key for crypto unit tests (no Spring context)
package com.docshelf.crypto;

import com.docshelf.config.DocshelfProperties;

final class TestKeys {

    static final String DEV_KEY_B64 = "ZG9jc2hlbGYtZGV2LW9ubHktbWFzdGVyLWtleS0zMmI=";

    private TestKeys() {
    }

    static DocshelfProperties props(String blobDir) {
        return new DocshelfProperties(DEV_KEY_B64, "test-token", blobDir, 200, "Asia/Kolkata",
                new DocshelfProperties.OpenAi("", "gpt-4.1", "gpt-4.1-mini", "text-embedding-3-small",
                        "gpt-4o-transcribe", true, 120, 2),
                new DocshelfProperties.Mail("docshelf@localhost", ""),
                new DocshelfProperties.WhatsApp(DocshelfProperties.WhatsApp.Provider.STUB, "", "", "tpl"));
    }

    static KeyService keyService() {
        return new KeyService(props("./target/test-blobs"));
    }
}
