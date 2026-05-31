package realestate.server.application.rag.application;

public record RagSearchResult(
        Long documentId,
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
