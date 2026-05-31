package realestate.server.application.rag.interfaces.dto;

import realestate.server.application.rag.domain.RagDocumentBuildResult;

public record RagDocumentBuildResponse(
        int upsertedCount
) {
    public static RagDocumentBuildResponse from(RagDocumentBuildResult result) {
        return new RagDocumentBuildResponse(result.upsertedCount());
    }
}
