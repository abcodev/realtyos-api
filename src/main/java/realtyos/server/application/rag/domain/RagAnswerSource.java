package realtyos.server.application.rag.domain;

public record RagAnswerSource(
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
    public static RagAnswerSource from(RagSearchResult result) {
        return new RagAnswerSource(
                result.documentId(),
                result.title(),
                result.apartmentName(),
                result.region(),
                result.sourceType(),
                result.sourceId(),
                result.searchType(),
                result.dealDate(),
                result.exclusiveArea(),
                result.dealAmount(),
                result.floor(),
                result.buildYear(),
                result.distance(),
                result.similarity(),
                result.finalScore()
        );
    }
}
