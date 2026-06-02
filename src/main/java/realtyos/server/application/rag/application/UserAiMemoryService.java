package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realtyos.server.application.rag.domain.RagQueryRewritePolicy;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.UserAiMemory;
import realtyos.server.application.rag.domain.UserAiMemoryEvent;
import realtyos.server.application.rag.domain.UserAiMemoryRepository;
import realtyos.server.application.realestate.domain.DecisionResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAiMemoryService {

    private static final int RECENT_EVENT_AGGREGATION_SIZE = 50;

    private final UserAiMemoryRepository repository;
    private final RagQueryRewritePolicy queryRewritePolicy = new RagQueryRewritePolicy();

    @Transactional(readOnly = true)
    public Optional<UserAiMemory> find(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findByUserId(userId);
    }

    public RagSearchCondition merge(Long userId, String query, RagSearchCondition condition) {
        RagSearchCondition inferredCondition = queryRewritePolicy.rewrite(query, condition).condition();
        return find(userId)
                .map(memory -> memory.mergeInto(inferredCondition))
                .orElse(inferredCondition);
    }

    @Transactional
    public void record(Long userId, String query, RagSearchCondition condition) {
        record(userId, query, condition, null, List.of(), null, null);
    }

    @Transactional
    public void record(
            Long userId,
            String query,
            RagSearchCondition condition,
            String answer,
            List<RagAnswerSource> sources,
            DecisionResult decision,
            String model
    ) {
        if (userId == null) {
            return;
        }
        RagSearchCondition inferredCondition = queryRewritePolicy.rewrite(query, condition).condition();
        repository.saveEvent(UserAiMemoryEvent.create(userId, query, inferredCondition, answer, sources, decision, model));
        String frequentRegion = findFrequentRegion(userId).orElse(inferredCondition.region());
        UserAiMemory current = repository.findByUserId(userId)
                .orElseGet(() -> UserAiMemory.empty(userId));
        repository.save(current.record(query, inferredCondition, frequentRegion));
    }

    @Transactional(readOnly = true)
    public List<UserAiMemoryEvent> findEvents(Long userId, Integer limit) {
        if (userId == null) {
            return List.of();
        }
        return repository.findRecentEvents(userId, normalizeLimit(limit));
    }

    @Transactional
    public UserAiMemory updatePreference(Long userId, String preferredRegion, Long minPrice, Long maxPrice) {
        UserAiMemory current = repository.findByUserId(userId)
                .orElseGet(() -> UserAiMemory.empty(userId));
        return repository.save(current.updatePreference(preferredRegion, minPrice, maxPrice));
    }

    @Transactional
    public void clear(Long userId) {
        repository.deleteEventsByUserId(userId);
        repository.deleteByUserId(userId);
    }

    private Optional<String> findFrequentRegion(Long userId) {
        return repository.findRecentRegionEvents(userId, RECENT_EVENT_AGGREGATION_SIZE)
                .stream()
                .collect(Collectors.groupingBy(
                        UserAiMemoryEvent::region,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Comparator
                        .comparingLong((Map.Entry<String, Long> entry) -> entry.getValue())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }
}
