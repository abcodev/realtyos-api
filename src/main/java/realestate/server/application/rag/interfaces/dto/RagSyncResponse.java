package realestate.server.application.rag.interfaces.dto;

import realestate.server.application.rag.domain.RagSyncResult;

public record RagSyncResponse(
        int upsertedDocumentCount,
        String embeddingProvider,
        String embeddingModel,
        int embeddedCount,
        int skippedCount,
        int failedCount
) {
    public static RagSyncResponse from(RagSyncResult result) {
        return new RagSyncResponse(
                result.upsertedDocumentCount(),
                result.embeddingProvider(),
                result.embeddingModel(),
                result.embeddedCount(),
                result.skippedCount(),
                result.failedCount()
        );
    }
}
