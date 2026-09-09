// Text embeddings (1536 dims for text-embedding-3-small); input must already be redacted
package com.docshelf.llm;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);

    int dimensions();
}
