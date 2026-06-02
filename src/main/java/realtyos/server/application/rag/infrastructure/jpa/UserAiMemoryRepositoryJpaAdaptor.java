package realtyos.server.application.rag.infrastructure.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.UserAiMemory;
import realtyos.server.application.rag.domain.UserAiMemoryEvent;
import realtyos.server.application.rag.domain.UserAiMemoryRepository;
import realtyos.server.application.realestate.domain.DecisionResult;
import realtyos.server.application.rag.infrastructure.jpa.entity.UserAiMemoryEventJpaEntity;
import realtyos.server.application.rag.infrastructure.jpa.entity.UserAiMemoryJpaEntity;
import realtyos.server.application.rag.infrastructure.jpa.repository.UserAiMemoryEventJpaRepository;
import realtyos.server.application.rag.infrastructure.jpa.repository.UserAiMemoryJpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserAiMemoryRepositoryJpaAdaptor implements UserAiMemoryRepository {

    private static final TypeReference<List<RagAnswerSource>> SOURCE_LIST_TYPE = new TypeReference<>() {
    };

    private final UserAiMemoryJpaRepository memoryJpaRepository;
    private final UserAiMemoryEventJpaRepository eventJpaRepository;
    private final ObjectMapper objectMapper;

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
        eventJpaRepository.save(toEntity(event));
    }

    @Override
    public List<UserAiMemoryEvent> findRecentEvents(Long userId, int limit) {
        return eventJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<UserAiMemoryEvent> findRecentRegionEvents(Long userId, int limit) {
        return eventJpaRepository.findByUserIdAndRegionIsNotNullOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteEventsByUserId(Long userId) {
        eventJpaRepository.deleteByUserId(userId);
    }

    private UserAiMemoryEventJpaEntity toEntity(UserAiMemoryEvent event) {
        return UserAiMemoryEventJpaEntity.create(
                event.userId(),
                event.query(),
                event.region(),
                event.apartmentName(),
                event.minPrice(),
                event.maxPrice(),
                event.answer(),
                writeJson(event.sources()),
                writeJson(event.decision()),
                event.model(),
                event.createdAt()
        );
    }

    private UserAiMemoryEvent toDomain(UserAiMemoryEventJpaEntity entity) {
        return new UserAiMemoryEvent(
                entity.getId(),
                entity.getUserId(),
                entity.getQuery(),
                entity.getRegion(),
                entity.getApartmentName(),
                entity.getMinPrice(),
                entity.getMaxPrice(),
                entity.getAnswer(),
                readSources(entity.getSourcesJson()),
                readDecision(entity.getDecisionJson()),
                entity.getModel(),
                entity.getCreatedAt()
        );
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 메모리 이벤트 JSON 저장에 실패했습니다.", e);
        }
    }

    private List<RagAnswerSource> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SOURCE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private DecisionResult readDecision(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DecisionResult.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
