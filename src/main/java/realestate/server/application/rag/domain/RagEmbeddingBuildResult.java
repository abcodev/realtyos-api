package realestate.server.application.rag.domain;

public record RagEmbeddingBuildResult(
        String provider,
        String model,
        int embeddedCount,
        int skippedCount,
        int failedCount
) {
}
