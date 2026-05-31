package realestate.server.application.rag.domain;

import java.util.List;

public record RagEmbeddingToSave(
        Long documentId,
        List<Double> embedding
) {
}
