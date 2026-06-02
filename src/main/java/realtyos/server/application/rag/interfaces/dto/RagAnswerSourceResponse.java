package realtyos.server.application.rag.interfaces.dto;

import realtyos.server.application.rag.domain.RagAnswerSource;

public record RagAnswerSourceResponse(
        Long documentId,
        String title,
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
        double finalScore
) {
    public static RagAnswerSourceResponse from(RagAnswerSource source) {
        return new RagAnswerSourceResponse(
                source.documentId(),
                source.title(),
                source.apartmentName(),
                source.region(),
                source.sourceType(),
                source.sourceId(),
                source.searchType(),
                source.dealDate(),
                source.exclusiveArea(),
                source.dealAmount(),
                source.floor(),
                source.buildYear(),
                source.distance(),
                source.similarity(),
                source.finalScore()
        );
    }
}
