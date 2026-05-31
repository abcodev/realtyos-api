package realestate.server.application.rag.interfaces.dto;

import realestate.server.application.rag.domain.RagSearchResult;

public record RagSearchResponse(
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
    public static RagSearchResponse from(RagSearchResult result) {
        return new RagSearchResponse(
                result.documentId(),
                result.embeddingProvider(),
                result.embeddingModel(),
                result.title(),
                result.content(),
                result.apartmentName(),
                result.region(),
                result.sourceType(),
                result.sourceId(),
                result.distance(),
                result.similarity()
        );
    }
}
