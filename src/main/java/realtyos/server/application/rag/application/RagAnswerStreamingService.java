package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagAiGateway;
import realtyos.server.application.rag.domain.RagAiRoute;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;
import realtyos.server.application.rag.domain.UserAiMemory;
import realtyos.server.application.realestate.application.service.RealestateDecisionService;
import realtyos.server.application.realestate.domain.DecisionResult;

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
    private final RagAnswerGuardrail guardrail;
    private final RealestateDecisionService decisionService;
    private final RagAnswerRouter answerRouter;

    public void stream(Long userId, String query, Integer topK, String embeddingProvider, String embeddingModel,
                       String answerProvider, String answerModel, RagSearchCondition condition,
                       Consumer<RagStreamEvent> eventConsumer) {
        Optional<UserAiMemory> memory = memoryService.find(userId);
        send(eventConsumer, "memory_loaded", Map.of(
                "enabled", userId != null,
                "exists", memory.isPresent()
        ));

        RagSearchCondition personalizedCondition = memoryService.merge(userId, query, condition);

        RagAnswerRoute route = answerRouter.route(query);
        send(eventConsumer, "route_selected", Map.of(
                "route", route.type().name(),
                "reason", route.reason()
        ));

        if (route.usesDecisionEngine()) {
            DecisionResult decision = decisionService.decide(query, topK, personalizedCondition);
            List<RagAnswerSource> sources = DecisionAnswerSourceMapper.from(decision);
            String answer = decisionService.formatAnswer(decision);
            memoryService.record(userId, query, decision.condition(), answer, sources, decision, "DECISION_ENGINE:" + route.type());
            send(eventConsumer, "retrieval_started", Map.of("query", query));
            send(eventConsumer, "retrieval_completed", Map.of("sourceCount", sources.size()));
            send(eventConsumer, "token", Map.of("text", answer));
            send(eventConsumer, "completed", Map.of(
                    "answer", answer,
                    "sourceCount", sources.size(),
                    "decision", decision,
                    "sources", sources
            ));
            return;
        }

        send(eventConsumer, "retrieval_started", Map.of("query", query));
        List<RagSearchResult> searchResults = searchService.search(
                query,
                topK,
                embeddingProvider,
                embeddingModel,
                personalizedCondition
        );
        send(eventConsumer, "retrieval_completed", Map.of("sourceCount", searchResults.size()));

        if (!guardrail.hasUsableEvidence(personalizedCondition, searchResults)) {
            String answer = guardrail.noMatchingEvidenceMessage();
            memoryService.record(userId, query, personalizedCondition, answer, List.of(), null, "SYSTEM");
            send(eventConsumer, "token", Map.of("text", answer));
            send(eventConsumer, "completed", Map.of(
                    "answer", answer,
                    "sourceCount", 0
            ));
            return;
        }

        List<RagAnswerSource> sources = searchResults.stream()
                .map(RagAnswerSource::from)
                .toList();
        if (guardrail.shouldUseEvidenceSummary(query)) {
            String answer = guardrail.buildEvidenceSummary(searchResults);
            memoryService.record(userId, query, personalizedCondition, answer, sources, null, "SYSTEM");
            send(eventConsumer, "token", Map.of("text", answer));
            send(eventConsumer, "completed", Map.of(
                    "answer", answer,
                    "sourceCount", sources.size(),
                    "sources", sources
            ));
            return;
        }

        String prompt = promptBuilder.build(query, searchResults, memory.map(UserAiMemory::toPromptContext).orElse(null));
        RagAiRoute aiRoute = aiGateway.route(ENTITY_TYPE, prompt, answerProvider, answerModel);
        send(eventConsumer, "model_selected", Map.of(
                "provider", aiRoute.provider(),
                "model", aiRoute.model() == null ? "" : aiRoute.model(),
                "reason", aiRoute.reason()
        ));

        StringBuilder answer = new StringBuilder();
        aiGateway.stream(aiRoute, ENTITY_TYPE, prompt, chunk -> {
            answer.append(chunk);
            send(eventConsumer, "token", Map.of("text", chunk));
        });

        memoryService.record(userId, query, personalizedCondition, answer.toString(), sources, null, RagModelName.of(aiRoute.provider(), aiRoute.model()));
        send(eventConsumer, "completed", Map.of(
                "answer", answer.toString(),
                "sourceCount", sources.size(),
                "sources", sources
        ));
    }

    private void send(Consumer<RagStreamEvent> eventConsumer, String eventName, Object data) {
        eventConsumer.accept(new RagStreamEvent(eventName, data));
    }
}
