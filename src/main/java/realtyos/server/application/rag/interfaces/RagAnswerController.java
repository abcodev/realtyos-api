package realtyos.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import realtyos.server.application.common.response.ApiResponse;
import realtyos.server.application.common.web.auth.CurrentUser;
import realtyos.server.application.rag.application.RagAnswerService;
import realtyos.server.application.rag.application.RagAnswerStreamingService;
import realtyos.server.application.rag.application.RagStreamEvent;
import realtyos.server.application.rag.interfaces.dto.RagAskRequest;
import realtyos.server.application.rag.interfaces.dto.RagAnswerResponse;
import realtyos.server.application.rag.interfaces.dto.RagSearchConditionMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG Answer", description = "RAG 답변 생성 API")
@Slf4j
public class RagAnswerController {

    private static final long SSE_TIMEOUT_MILLIS = 300_000L;

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

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG 답변 스트리밍 생성", description = "RAG 검색과 AI 답변 생성을 SSE 이벤트로 스트리밍합니다.")
    public ResponseEntity<SseEmitter> askStream(
            @CurrentUser(required = false) Long userId,
            @RequestBody @Valid RagAskRequest request
    ) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> {
            try {
                streamingService.stream(
                        userId,
                        request.query(),
                        request.topK(),
                        request.embeddingProvider(),
                        request.embeddingModel(),
                        request.answerProvider(),
                        request.answerModel(),
                        RagSearchConditionMapper.from(request),
                        event -> send(emitter, event)
                );
                emitter.complete();
            } catch (Exception e) {
                log.error("RAG answer streaming failed - query: {}", request.query(), e);
                send(emitter, new RagStreamEvent(
                        "error",
                        Map.of("message", e.getMessage() == null ? "streaming failed" : e.getMessage())
                ));
                emitter.completeWithError(e);
            }
        });
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private void send(SseEmitter emitter, RagStreamEvent event) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name(event.name())
                        .data(event.data()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("SSE 이벤트 전송에 실패했습니다: " + event.name(), e);
        }
    }
}
