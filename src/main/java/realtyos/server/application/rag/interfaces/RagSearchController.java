package realtyos.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realtyos.server.application.common.response.ApiResponse;
import realtyos.server.application.common.web.auth.CurrentUser;
import realtyos.server.application.rag.application.RagSearchService;
import realtyos.server.application.rag.application.UserAiMemoryService;
import realtyos.server.application.rag.interfaces.dto.RagSearchRequest;
import realtyos.server.application.rag.interfaces.dto.RagSearchConditionMapper;
import realtyos.server.application.rag.interfaces.dto.RagSearchResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG Search", description = "RAG 검색 API")
public class RagSearchController {

    private final RagSearchService searchService;
    private final UserAiMemoryService memoryService;

    @PostMapping("/search")
    @Operation(summary = "RAG 문서 유사도 검색", description = "질문을 embedding으로 변환한 뒤 pgvector cosine distance 기준으로 가까운 RAG 문서를 조회합니다.")
    public ApiResponse<List<RagSearchResponse>> search(
            @CurrentUser(required = false) Long userId,
            @RequestBody @Valid RagSearchRequest request
    ) {
        var condition = memoryService.merge(userId, request.query(), RagSearchConditionMapper.from(request));
        var results = searchService.search(
                        request.query(),
                        request.topK(),
                        request.embeddingProvider(),
                        request.embeddingModel(),
                        condition);
        memoryService.record(userId, request.query(), condition);
        return ApiResponse.success(results.stream()
                .map(RagSearchResponse::from)
                .toList());
    }
}
