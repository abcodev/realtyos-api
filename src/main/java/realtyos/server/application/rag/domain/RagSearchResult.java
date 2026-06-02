package realtyos.server.application.rag.domain;

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
        String searchType,
        String dealDate,
        String exclusiveArea,
        Long dealAmount,
        String floor,
        String buildYear,
        double distance,
        double similarity,
        double recencyScore,
        double finalScore
) {
}
