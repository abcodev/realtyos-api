package realtyos.server.application.rag.domain;

import java.time.LocalDateTime;

public record UserAiMemoryEvent(
        Long id,
        Long userId,
        String query,
        String region,
        String apartmentName,
        Long minPrice,
        Long maxPrice,
        LocalDateTime createdAt
) {

    public static UserAiMemoryEvent create(Long userId, String query, RagSearchCondition condition) {
        return new UserAiMemoryEvent(
                null,
                userId,
                query,
                condition == null ? null : blankToNull(condition.region()),
                condition == null ? null : blankToNull(condition.apartmentName()),
                condition == null ? null : condition.minPrice(),
                condition == null ? null : condition.maxPrice(),
                LocalDateTime.now()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
