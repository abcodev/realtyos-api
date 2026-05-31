package realestate.server.application.rag.domain;

public record RagSearchResult(
        Long documentId,
        String embeddingProvider,
        String embeddingModel,
        String title,
        String content,
        String apartmentName,
        String region,
        String sourceType,
        Long sourceId,
        double distance,
        double similarity
) {
}
