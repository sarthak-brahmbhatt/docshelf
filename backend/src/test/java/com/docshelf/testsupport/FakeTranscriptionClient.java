// TranscriptionClient test double: returns the canned 'transcribe' response or a default transcript
package com.docshelf.testsupport;

import com.docshelf.llm.LlmRequest;
import com.docshelf.llm.Transcript;
import com.docshelf.llm.TranscriptionClient;

public class FakeTranscriptionClient implements TranscriptionClient {

    public static final String DEFAULT_TEXT = "This is a fake transcript.";

    private final FakeResponseRegistry registry;

    public FakeTranscriptionClient(FakeResponseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Transcript transcribe(byte[] audio, String mimeType, String languageHint) {
        LlmRequest req = LlmRequest.of(FakeResponseRegistry.TRANSCRIBE_PURPOSE, mimeType,
                "audio bytes=" + (audio == null ? 0 : audio.length));
        String text = registry.has(FakeResponseRegistry.TRANSCRIBE_PURPOSE) ? registry.respond(req) : DEFAULT_TEXT;
        return new Transcript(text, languageHint == null ? "en" : languageHint, 3.5);
    }
}
