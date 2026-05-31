package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realtyos.server.application.common.ai.AiProvider;
import realtyos.server.application.common.ai.AiService;
import realtyos.server.application.rag.domain.RagAnswer;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;
import realtyos.server.application.rag.memory.UserAiMemory;
import realtyos.server.application.rag.memory.UserAiMemoryService;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private static final String ENTITY_TYPE = "RAG_REALESTATE";

    private final RagSearchService searchService;
    private final AiService aiService;
    private final UserAiMemoryService memoryService;
    private final RagAnswerPromptBuilder promptBuilder;

    public RagAnswer answer(Long userId, String query, Integer topK, String embeddingProvider, String embeddingModel,
                            String answerProvider, String answerModel, RagSearchCondition condition) {
        Optional<UserAiMemory> memory = memoryService.find(userId);
        RagSearchCondition personalizedCondition = memoryService.merge(userId, query, condition);

        List<RagSearchResult> searchResults = searchService.search(
                query,
                topK,
                embeddingProvider,
                embeddingModel,
                personalizedCondition
        );
        if (searchResults.isEmpty()) {
            memoryService.record(userId, query, personalizedCondition);
            return new RagAnswer("관련 실거래가 문서를 찾지 못했습니다.", List.of());
        }

        String prompt = promptBuilder.build(query, searchResults, memory.map(UserAiMemory::toPromptContext).orElse(null));
        String answer = aiService.askRouted(ENTITY_TYPE, prompt, resolveAnswerProvider(answerProvider), answerModel);
        List<RagAnswerSource> sources = searchResults.stream()
                .map(RagAnswerSource::from)
                .toList();

        memoryService.record(userId, query, personalizedCondition);
        log.info("RAG answer completed - query: {}, sourceCount: {}", query, sources.size());
        return new RagAnswer(answer, sources);
    }

    private AiProvider resolveAnswerProvider(String answerProvider) {
        if (answerProvider == null || answerProvider.isBlank()) {
            return null;
        }
        return AiProvider.valueOf(answerProvider.trim().toUpperCase());
    }

}
