package realtyos.server.application.rag.infrastructure.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import realtyos.server.application.rag.domain.UserAiMemory;
import realtyos.server.application.rag.domain.UserAiMemoryEvent;
import realtyos.server.application.rag.domain.UserAiMemoryRepository;
import realtyos.server.application.rag.infrastructure.jpa.entity.UserAiMemoryEventJpaEntity;
import realtyos.server.application.rag.infrastructure.jpa.entity.UserAiMemoryJpaEntity;
import realtyos.server.application.rag.infrastructure.jpa.repository.UserAiMemoryEventJpaRepository;
import realtyos.server.application.rag.infrastructure.jpa.repository.UserAiMemoryJpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserAiMemoryRepositoryJpaAdaptor implements UserAiMemoryRepository {

    private final UserAiMemoryJpaRepository memoryJpaRepository;
    private final UserAiMemoryEventJpaRepository eventJpaRepository;

    @Override
    public Optional<UserAiMemory> findByUserId(Long userId) {
        return memoryJpaRepository.findByUserId(userId)
                .map(UserAiMemoryJpaEntity::toDomain);
    }

    @Override
    public UserAiMemory save(UserAiMemory memory) {
        UserAiMemoryJpaEntity entity = memoryJpaRepository.findByUserId(memory.userId())
                .orElseGet(() -> UserAiMemoryJpaEntity.from(memory));
        entity.apply(memory);
        return memoryJpaRepository.save(entity).toDomain();
    }

    @Override
    public void deleteByUserId(Long userId) {
        memoryJpaRepository.deleteByUserId(userId);
    }

    @Override
    public void saveEvent(UserAiMemoryEvent event) {
        eventJpaRepository.save(UserAiMemoryEventJpaEntity.from(event));
    }

    @Override
    public List<UserAiMemoryEvent> findRecentEvents(Long userId, int limit) {
        return eventJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(UserAiMemoryEventJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UserAiMemoryEvent> findRecentRegionEvents(Long userId, int limit) {
        return eventJpaRepository.findByUserIdAndRegionIsNotNullOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(UserAiMemoryEventJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteEventsByUserId(Long userId) {
        eventJpaRepository.deleteByUserId(userId);
    }
}
