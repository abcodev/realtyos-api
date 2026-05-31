package realtyos.server.application.rag.application;

import org.springframework.stereotype.Component;
import realtyos.server.application.rag.domain.RagSearchResult;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagAnswerPromptBuilder {

    public String build(String query, List<RagSearchResult> searchResults, String memoryContext) {
        String context = searchResults.stream()
                .map(this::formatDocument)
                .collect(Collectors.joining("\n\n"));
        String userMemory = memoryContext == null || memoryContext.isBlank()
                ? "저장된 사용자 메모리가 없습니다."
                : memoryContext;

        return """
                아래 RAG 문서만 근거로 사용자 질문에 답변하세요.
                사용자 메모리는 답변의 관점과 우선순위를 정하는 데만 사용하고, 사실 근거는 반드시 RAG 문서에서만 가져오세요.
                문서에 없는 사실은 추정하지 말고 알 수 없다고 답하세요.
                답변에는 가능한 경우 아래 표를 포함하세요.
                | 아파트명 | 거래일 | 전용면적 | 거래금액 | 층 | 근거문서ID |
                금액은 만원 단위 원문과 억원 환산 표현을 함께 사용하세요.
                검색 결과가 여러 건이면 최근 거래와 유사도가 높은 거래를 우선 요약하세요.

                [사용자 메모리]
                %s

                [RAG 문서]
                %s

                [사용자 질문]
                %s
                """.formatted(userMemory, context, query);
    }

    private String formatDocument(RagSearchResult result) {
        return """
                문서ID: %d
                제목: %s
                거래일: %s
                아파트명: %s
                전용면적: %s㎡
                거래금액: %s만원
                층: %s
                유사도: %.4f
                최종점수: %.4f
                내용:
                %s
                """.formatted(
                result.documentId(),
                result.title(),
                result.dealDate(),
                result.apartmentName(),
                result.exclusiveArea(),
                result.dealAmount(),
                result.floor(),
                result.similarity(),
                result.finalScore(),
                result.content()
        );
    }
}
