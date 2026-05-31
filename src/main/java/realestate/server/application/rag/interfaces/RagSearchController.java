package realestate.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realestate.server.application.common.response.ApiResponse;
import realestate.server.application.rag.application.RagSearchResult;
import realestate.server.application.rag.application.RagSearchService;
import realestate.server.application.rag.interfaces.dto.RagSearchRequest;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG Search", description = "RAG 검색 API")
public class RagSearchController {

    private final RagSearchService searchService;

    @PostMapping("/search")
    @Operation(summary = "RAG 문서 유사도 검색", description = "질문을 embedding으로 변환한 뒤 pgvector cosine distance 기준으로 가까운 RAG 문서를 조회합니다.")
    public ApiResponse<List<RagSearchResult>> search(@RequestBody RagSearchRequest request) {
        return ApiResponse.success(searchService.search(request.query(), request.topK()));
    }
}
