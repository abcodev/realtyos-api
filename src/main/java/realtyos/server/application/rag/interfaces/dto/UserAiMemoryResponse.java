package realtyos.server.application.rag.interfaces.dto;

import realtyos.server.application.rag.domain.UserAiMemory;

public record UserAiMemoryResponse(
        String preferredRegion,
        String recentRegion,
        Long minPrice,
        Long maxPrice,
        long queryCount,
        String lastQuery
) {

    public static UserAiMemoryResponse empty() {
        return new UserAiMemoryResponse(null, null, null, null, 0, null);
    }

    public static UserAiMemoryResponse from(UserAiMemory memory) {
        return new UserAiMemoryResponse(
                memory.preferredRegion(),
                memory.recentRegion(),
                memory.minPrice(),
                memory.maxPrice(),
                memory.queryCount(),
                memory.lastQuery()
        );
    }
}
