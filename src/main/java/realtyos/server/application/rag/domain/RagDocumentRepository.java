package realtyos.server.application.rag.domain;

import java.util.List;

public interface RagDocumentRepository {

    int buildDealDocuments(int limit);

    List<RagDocumentForEmbedding> findDocumentsWithoutEmbedding(EmbeddingModelProfile profile, int limit);

    int saveEmbeddings(EmbeddingModelProfile profile, List<RagEmbeddingToSave> embeddings);

    List<RagSearchResult> searchByEmbedding(
            EmbeddingModelProfile profile,
            List<Double> embedding,
            int topK,
            RagSearchCondition condition
    );

    List<RagSearchResult> searchDealsByKeyword(int topK, RagSearchCondition condition);

    RagIndexStats getIndexStats(EmbeddingModelProfile profile);
}
