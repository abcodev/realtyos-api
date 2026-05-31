package realestate.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realestate.server.application.common.response.ApiResponse;
import realestate.server.application.rag.application.RagAnswerService;
import realestate.server.application.rag.interfaces.dto.RagAskRequest;
import realestate.server.application.rag.interfaces.dto.RagAnswerResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG Answer", description = "RAG 답변 생성 API")
public class RagAnswerController {

    private final RagAnswerService answerService;

    @PostMapping("/ask")
    @Operation(summary = "RAG 답변 생성", description = "질문과 가까운 RAG 문서를 검색한 뒤, 검색된 문서를 근거로 AI 답변을 생성합니다.")
    public ApiResponse<RagAnswerResponse> ask(@RequestBody @Valid RagAskRequest request) {
        return ApiResponse.success(RagAnswerResponse.from(answerService.answer(
                request.query(),
                request.topK(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.answerProvider(),
                request.answerModel())));
    }
}
