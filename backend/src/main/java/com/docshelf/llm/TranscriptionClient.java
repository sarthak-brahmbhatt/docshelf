// Audio to text (voice notes and voice commands)
package com.docshelf.llm;

public interface TranscriptionClient {

    Transcript transcribe(byte[] audio, String mimeType, String languageHint);
}
