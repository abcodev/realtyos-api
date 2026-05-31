package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realestate.server.application.common.ai.AiProvider;
import realestate.server.application.common.ai.AiService;
import realestate.server.application.rag.domain.RagAnswer;
import realestate.server.application.rag.domain.RagAnswerSource;
import realestate.server.application.rag.domain.RagSearchResult;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagAnswerService {

    private static final String ENTITY_TYPE = "RAG_REALESTATE";

    private final RagSearchService searchService;
    private final AiService aiService;

    public RagAnswer answer(String query, Integer topK, String embeddingProvider, String embeddingModel,
                            String answerProvider, String answerModel) {
        List<RagSearchResult> searchResults = searchService.search(query, topK, embeddingProvider, embeddingModel);
        if (searchResults.isEmpty()) {
            return new RagAnswer("관련 실거래가 문서를 찾지 못했습니다.", List.of());
        }

        String prompt = buildPrompt(query, searchResults);
        String answer = aiService.ask(resolveAnswerProvider(answerProvider), ENTITY_TYPE, prompt, answerModel);
        List<RagAnswerSource> sources = searchResults.stream()
                .map(RagAnswerSource::from)
                .toList();

        log.info("RAG answer completed - query: {}, sourceCount: {}", query, sources.size());
        return new RagAnswer(answer, sources);
    }

    private AiProvider resolveAnswerProvider(String answerProvider) {
        if (answerProvider == null || answerProvider.isBlank()) {
            return AiProvider.OPENAI;
        }
        return AiProvider.valueOf(answerProvider.trim().toUpperCase());
    }

    private String buildPrompt(String query, List<RagSearchResult> searchResults) {
        String context = searchResults.stream()
                .map(this::formatDocument)
                .collect(Collectors.joining("\n\n"));

        return """
                아래 RAG 문서만 근거로 사용자 질문에 답변하세요.
                문서에 없는 사실은 추정하지 말고 알 수 없다고 답하세요.

                [RAG 문서]
                %s

                [사용자 질문]
                %s
                """.formatted(context, query);
    }

    private String formatDocument(RagSearchResult result) {
        return """
                문서ID: %d
                제목: %s
                유사도: %.4f
                내용:
                %s
                """.formatted(
                result.documentId(),
                result.title(),
                result.similarity(),
                result.content()
        );
    }
}
