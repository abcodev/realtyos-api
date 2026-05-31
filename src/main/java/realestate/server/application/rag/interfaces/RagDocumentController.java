package realestate.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import realestate.server.application.common.response.ApiResponse;
import realestate.server.application.rag.application.RagDocumentBuildService;
import realestate.server.application.rag.application.RagEmbeddingBuildService;
import realestate.server.application.rag.application.RagSyncService;
import realestate.server.application.rag.interfaces.dto.RagDocumentBuildResponse;
import realestate.server.application.rag.interfaces.dto.RagEmbeddingBuildResponse;
import realestate.server.application.rag.interfaces.dto.RagSyncResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag/documents")
@Tag(name = "RAG Documents", description = "RAG 문서 생성 API")
public class RagDocumentController {

    private final RagDocumentBuildService buildService;
    private final RagEmbeddingBuildService embeddingBuildService;
    private final RagSyncService syncService;

    @PostMapping("/deals")
    @Operation(summary = "실거래가 RAG 문서 생성/갱신", description = "real_estate_deals 데이터를 rag_document 문서로 변환해 upsert합니다. 내용이 바뀌면 기존 embedding을 무효화합니다. limit이 0 이하이면 전체를 처리합니다.")
    public ApiResponse<RagDocumentBuildResponse> buildDealDocuments(
            @RequestParam(defaultValue = "1000") int limit) {
        return ApiResponse.success(RagDocumentBuildResponse.from(buildService.buildDealDocuments(limit)));
    }

    @PostMapping("/embeddings")
    @Operation(summary = "RAG 문서 임베딩 생성", description = "아직 embedding이 없는 rag_document 문서를 선택한 provider/model embedding으로 변환해 rag_embedding에 저장합니다. limit이 0 이하이면 전체를 처리합니다.")
    public ApiResponse<RagEmbeddingBuildResponse> buildDocumentEmbeddings(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model) {
        return ApiResponse.success(RagEmbeddingBuildResponse.from(
                embeddingBuildService.buildDocumentEmbeddings(limit, provider, model)));
    }

    @PostMapping("/sync")
    @Operation(summary = "실거래가 RAG 문서/임베딩 동기화", description = "실거래가 RAG 문서를 upsert하고, 선택한 provider/model 기준으로 누락된 embedding을 생성합니다.")
    public ApiResponse<RagSyncResponse> syncDealDocumentsAndEmbeddings(
            @RequestParam(defaultValue = "1000") int documentLimit,
            @RequestParam(defaultValue = "1000") int embeddingLimit,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model) {
        return ApiResponse.success(RagSyncResponse.from(
                syncService.syncDealDocumentsAndEmbeddings(documentLimit, embeddingLimit, provider, model)));
    }
}
