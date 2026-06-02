package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagAiGateway;
import realtyos.server.application.rag.domain.RagAnswer;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;
import realtyos.server.application.rag.domain.UserAiMemory;
import realtyos.server.application.realestate.application.service.RealestateDecisionService;
import realtyos.server.application.realestate.domain.DecisionResult;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private static final String ENTITY_TYPE = "RAG_REALESTATE";

    private final RagSearchService searchService;
    private final RagAiGateway aiGateway;
    private final UserAiMemoryService memoryService;
    private final RagAnswerPromptBuilder promptBuilder;
    private final RagAnswerGuardrail guardrail;
    private final RealestateDecisionService decisionService;

    public RagAnswer answer(Long userId, String query, Integer topK, String embeddingProvider, String embeddingModel,
                            String answerProvider, String answerModel, RagSearchCondition condition) {
        Optional<UserAiMemory> memory = memoryService.find(userId);
        RagSearchCondition personalizedCondition = memoryService.merge(userId, query, condition);

        if (decisionService.supports(query)) {
            DecisionResult decision = decisionService.decide(query, topK, personalizedCondition);
            memoryService.record(userId, query, decision.condition());
            return new RagAnswer(
                    decisionService.formatAnswer(decision),
                    DecisionAnswerSourceMapper.from(decision),
                    decision
            );
        }

        List<RagSearchResult> searchResults = searchService.search(
                query,
                topK,
                embeddingProvider,
                embeddingModel,
                personalizedCondition
        );
        if (!guardrail.hasUsableEvidence(personalizedCondition, searchResults)) {
            memoryService.record(userId, query, personalizedCondition);
            return new RagAnswer(guardrail.noMatchingEvidenceMessage(), List.of());
        }

        List<RagAnswerSource> sources = searchResults.stream()
                .map(RagAnswerSource::from)
                .toList();
        if (guardrail.shouldUseEvidenceSummary(query)) {
            memoryService.record(userId, query, personalizedCondition);
            return new RagAnswer(guardrail.buildEvidenceSummary(searchResults), sources);
        }

        String prompt = promptBuilder.build(query, searchResults, memory.map(UserAiMemory::toPromptContext).orElse(null));
        String answer = aiGateway.askRouted(ENTITY_TYPE, prompt, answerProvider, answerModel);
        answer = guardrail.finalizeAnswer(answer, searchResults);

        memoryService.record(userId, query, personalizedCondition);
        log.info("RAG answer completed - query: {}, sourceCount: {}", query, sources.size());
        return new RagAnswer(answer, sources);
    }

}
