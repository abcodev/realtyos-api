package realestate.server.application.rag.domain;

import java.util.List;

public interface RagDocumentRepository {

    int buildDealDocuments(int limit);

    List<RagDocumentForEmbedding> findDocumentsWithoutEmbedding(EmbeddingModelProfile profile, int limit);

    int saveEmbedding(Long documentId, EmbeddingModelProfile profile, List<Double> embedding);

    List<RagSearchResult> searchByEmbedding(EmbeddingModelProfile profile, List<Double> embedding, int topK);
}
