package realtyos.server.application.rag.domain;

import java.time.LocalDateTime;
import java.util.List;
import realtyos.server.application.realestate.domain.DecisionResult;

public record UserAiMemoryEvent(
        Long id,
        Long userId,
        String query,
        String region,
        String apartmentName,
        Long minPrice,
        Long maxPrice,
        String answer,
        List<RagAnswerSource> sources,
        DecisionResult decision,
        String model,
        LocalDateTime createdAt
) {

    public static UserAiMemoryEvent create(Long userId, String query, RagSearchCondition condition) {
        return create(userId, query, condition, null, List.of(), null, null);
    }

    public static UserAiMemoryEvent create(
            Long userId,
            String query,
            RagSearchCondition condition,
            String answer,
            List<RagAnswerSource> sources,
            DecisionResult decision,
            String model
    ) {
        return new UserAiMemoryEvent(
                null,
                userId,
                query,
                condition == null ? null : blankToNull(condition.region()),
                condition == null ? null : blankToNull(condition.apartmentName()),
                condition == null ? null : condition.minPrice(),
                condition == null ? null : condition.maxPrice(),
                blankToNull(answer),
                sources == null ? List.of() : sources,
                decision,
                blankToNull(model),
                LocalDateTime.now()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
