package realtyos.server.application.rag.interfaces.dto;

import realtyos.server.application.rag.domain.UserAiMemoryEvent;

import java.time.LocalDateTime;
import java.util.List;

public record UserAiMemoryEventResponse(
        Long id,
        String query,
        String region,
        String apartmentName,
        Long minPrice,
        Long maxPrice,
        String answer,
        List<RagAnswerSourceResponse> sources,
        RagDecisionResponse decision,
        String model,
        LocalDateTime createdAt
) {

    public static UserAiMemoryEventResponse from(UserAiMemoryEvent event) {
        return new UserAiMemoryEventResponse(
                event.id(),
                event.query(),
                event.region(),
                event.apartmentName(),
                event.minPrice(),
                event.maxPrice(),
                event.answer(),
                event.sources().stream()
                        .map(RagAnswerSourceResponse::from)
                        .toList(),
                RagDecisionResponse.from(event.decision()),
                event.model(),
                event.createdAt()
        );
    }
}
