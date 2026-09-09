// Image + instructions to typed JSON via the Responses API with input_image (pixels cannot be redacted; see design 7.4)
package com.docshelf.llm;

public interface VisionClient {

    <T> T extractFromImage(byte[] image, String mimeType, String instructions, Class<T> schemaType);
}
