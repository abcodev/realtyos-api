package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagAiGateway;
import realtyos.server.application.rag.domain.RagAiRoute;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;
import realtyos.server.application.rag.domain.UserAiMemory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class RagAnswerStreamingService {

    private static final String ENTITY_TYPE = "RAG_REALESTATE";

    private final RagSearchService searchService;
    private final RagAiGateway aiGateway;
    private final UserAiMemoryService memoryService;
    private final RagAnswerPromptBuilder promptBuilder;

    public void stream(Long userId, String query, Integer topK, String embeddingProvider, String embeddingModel,
                       String answerProvider, String answerModel, RagSearchCondition condition,
                       Consumer<RagStreamEvent> eventConsumer) {
        Optional<UserAiMemory> memory = memoryService.find(userId);
        send(eventConsumer, "memory_loaded", Map.of(
                "enabled", userId != null,
                "exists", memory.isPresent()
        ));

        RagSearchCondition personalizedCondition = memoryService.merge(userId, query, condition);

        send(eventConsumer, "retrieval_started", Map.of("query", query));
        List<RagSearchResult> searchResults = searchService.search(
                query,
                topK,
                embeddingProvider,
                embeddingModel,
                personalizedCondition
        );
        send(eventConsumer, "retrieval_completed", Map.of("sourceCount", searchResults.size()));

        if (searchResults.isEmpty()) {
            memoryService.record(userId, query, personalizedCondition);
            send(eventConsumer, "token", Map.of("text", "관련 실거래가 문서를 찾지 못했습니다."));
            send(eventConsumer, "completed", Map.of("sourceCount", 0));
            return;
        }

        String prompt = promptBuilder.build(query, searchResults, memory.map(UserAiMemory::toPromptContext).orElse(null));
        RagAiRoute route = aiGateway.route(ENTITY_TYPE, prompt, answerProvider, answerModel);
        send(eventConsumer, "model_selected", Map.of(
                "provider", route.provider(),
                "model", route.model() == null ? "" : route.model(),
                "reason", route.reason()
        ));

        aiGateway.stream(route, ENTITY_TYPE, prompt, chunk -> send(eventConsumer, "token", Map.of("text", chunk)));

        List<RagAnswerSource> sources = searchResults.stream()
                .map(RagAnswerSource::from)
                .toList();
        memoryService.record(userId, query, personalizedCondition);
        send(eventConsumer, "completed", Map.of(
                "sourceCount", sources.size(),
                "sources", sources
        ));
    }

    private void send(Consumer<RagStreamEvent> eventConsumer, String eventName, Object data) {
        eventConsumer.accept(new RagStreamEvent(eventName, data));
    }
}
