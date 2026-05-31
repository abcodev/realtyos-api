package realestate.server.application.rag.domain;

public record RagSyncResult(
        int upsertedDocumentCount,
        String embeddingProvider,
        String embeddingModel,
        int embeddedCount,
        int skippedCount,
        int failedCount
) {
    public static RagSyncResult of(RagDocumentBuildResult documentResult, RagEmbeddingBuildResult embeddingResult) {
        return new RagSyncResult(
                documentResult.upsertedCount(),
                embeddingResult.provider(),
                embeddingResult.model(),
                embeddingResult.embeddedCount(),
                embeddingResult.skippedCount(),
                embeddingResult.failedCount()
        );
    }
}
