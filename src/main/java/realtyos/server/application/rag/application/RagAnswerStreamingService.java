package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import realtyos.server.application.common.ai.AiProvider;
import realtyos.server.application.common.ai.AiService;
import realtyos.server.application.common.ai.routing.AiRoute;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;
import realtyos.server.application.rag.memory.UserAiMemory;
import realtyos.server.application.rag.memory.UserAiMemoryService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagAnswerStreamingService {

    private static final String ENTITY_TYPE = "RAG_REALESTATE";
    private static final long SSE_TIMEOUT_MILLIS = 120_000L;

    private final RagSearchService searchService;
    private final AiService aiService;
    private final UserAiMemoryService memoryService;
    private final RagAnswerPromptBuilder promptBuilder;

    public SseEmitter stream(Long userId, String query, Integer topK, String embeddingProvider, String embeddingModel,
                             String answerProvider, String answerModel, RagSearchCondition condition) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        CompletableFuture.runAsync(() -> {
            try {
                runStream(emitter, userId, query, topK, embeddingProvider, embeddingModel,
                        answerProvider, answerModel, condition);
                emitter.complete();
            } catch (Exception e) {
                log.error("RAG answer streaming failed - query: {}", query, e);
                send(emitter, "error", Map.of("message", e.getMessage() == null ? "streaming failed" : e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void runStream(SseEmitter emitter, Long userId, String query, Integer topK, String embeddingProvider,
                           String embeddingModel, String answerProvider, String answerModel,
                           RagSearchCondition condition) {
        Optional<UserAiMemory> memory = memoryService.find(userId);
        send(emitter, "memory_loaded", Map.of(
                "enabled", userId != null,
                "exists", memory.isPresent()
        ));

        RagSearchCondition personalizedCondition = memoryService.merge(userId, query, condition);

        send(emitter, "retrieval_started", Map.of("query", query));
        List<RagSearchResult> searchResults = searchService.search(
                query,
                topK,
                embeddingProvider,
                embeddingModel,
                personalizedCondition
        );
        send(emitter, "retrieval_completed", Map.of("sourceCount", searchResults.size()));

        if (searchResults.isEmpty()) {
            memoryService.record(userId, query, personalizedCondition);
            send(emitter, "token", Map.of("text", "관련 실거래가 문서를 찾지 못했습니다."));
            send(emitter, "completed", Map.of("sourceCount", 0));
            return;
        }

        String prompt = promptBuilder.build(query, searchResults, memory.map(UserAiMemory::toPromptContext).orElse(null));
        AiRoute route = aiService.route(ENTITY_TYPE, prompt, resolveAnswerProvider(answerProvider), answerModel);
        send(emitter, "model_selected", Map.of(
                "provider", route.provider().name(),
                "model", route.model() == null ? "" : route.model(),
                "reason", route.reason()
        ));

        aiService.stream(route, ENTITY_TYPE, prompt, chunk -> send(emitter, "token", Map.of("text", chunk)));

        List<RagAnswerSource> sources = searchResults.stream()
                .map(RagAnswerSource::from)
                .toList();
        memoryService.record(userId, query, personalizedCondition);
        send(emitter, "completed", Map.of(
                "sourceCount", sources.size(),
                "sources", sources
        ));
    }

    private AiProvider resolveAnswerProvider(String answerProvider) {
        if (answerProvider == null || answerProvider.isBlank()) {
            return null;
        }
        return AiProvider.valueOf(answerProvider.trim().toUpperCase());
    }

    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            }
        } catch (IOException e) {
            throw new IllegalStateException("SSE 이벤트 전송에 실패했습니다: " + eventName, e);
        }
    }
}
