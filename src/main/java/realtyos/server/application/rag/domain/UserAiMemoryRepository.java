package realtyos.server.application.rag.domain;

import java.util.List;
import java.util.Optional;

public interface UserAiMemoryRepository {

    Optional<UserAiMemory> findByUserId(Long userId);

    UserAiMemory save(UserAiMemory memory);

    void deleteByUserId(Long userId);

    void saveEvent(UserAiMemoryEvent event);

    List<UserAiMemoryEvent> findRecentEvents(Long userId, int limit);

    List<UserAiMemoryEvent> findRecentRegionEvents(Long userId, int limit);

    void deleteEventsByUserId(Long userId);
}
