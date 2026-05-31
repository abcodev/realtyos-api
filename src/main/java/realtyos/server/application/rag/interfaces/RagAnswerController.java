package realtyos.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import realtyos.server.application.common.response.ApiResponse;
import realtyos.server.application.common.web.auth.CurrentUser;
import realtyos.server.application.rag.application.RagAnswerService;
import realtyos.server.application.rag.application.RagAnswerStreamingService;
import realtyos.server.application.rag.interfaces.dto.RagAskRequest;
import realtyos.server.application.rag.interfaces.dto.RagAnswerResponse;
import realtyos.server.application.rag.interfaces.dto.RagSearchConditionMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG Answer", description = "RAG 답변 생성 API")
public class RagAnswerController {

    private final RagAnswerService answerService;
    private final RagAnswerStreamingService streamingService;

    @PostMapping("/ask")
    @Operation(summary = "RAG 답변 생성", description = "질문과 가까운 RAG 문서를 검색한 뒤, 검색된 문서를 근거로 AI 답변을 생성합니다.")
    public ApiResponse<RagAnswerResponse> ask(
            @CurrentUser(required = false) Long userId,
            @RequestBody @Valid RagAskRequest request
    ) {
        return ApiResponse.success(RagAnswerResponse.from(answerService.answer(
                userId,
                request.query(),
                request.topK(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.answerProvider(),
                request.answerModel(),
                RagSearchConditionMapper.from(request))));
    }

    @PostMapping("/ask/stream")
    @Operation(summary = "RAG 답변 스트리밍 생성", description = "RAG 검색과 AI 답변 생성을 SSE 이벤트로 스트리밍합니다.")
    public SseEmitter askStream(
            @CurrentUser(required = false) Long userId,
            @RequestBody @Valid RagAskRequest request
    ) {
        return streamingService.stream(
                userId,
                request.query(),
                request.topK(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.answerProvider(),
                request.answerModel(),
                RagSearchConditionMapper.from(request));
    }
}
