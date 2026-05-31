package realtyos.server.application.rag.interfaces.dto;

import realtyos.server.application.rag.domain.UserAiMemoryEvent;

import java.time.LocalDateTime;

public record UserAiMemoryEventResponse(
        Long id,
        String query,
        String region,
        String apartmentName,
        Long minPrice,
        Long maxPrice,
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
                event.createdAt()
        );
    }
}
