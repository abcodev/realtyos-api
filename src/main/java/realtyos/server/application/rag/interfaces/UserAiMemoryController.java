package realtyos.server.application.rag.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import realtyos.server.application.common.response.ApiResponse;
import realtyos.server.application.common.web.auth.CurrentUser;
import realtyos.server.application.rag.interfaces.dto.UserAiMemoryEventResponse;
import realtyos.server.application.rag.interfaces.dto.UserAiMemoryResponse;
import realtyos.server.application.rag.interfaces.dto.UserAiMemoryUpdateRequest;
import realtyos.server.application.rag.application.UserAiMemoryService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rag/memory")
@Tag(name = "RAG Memory", description = "사용자 AI 메모리 API")
public class UserAiMemoryController {

    private final UserAiMemoryService memoryService;

    @GetMapping("/me")
    @Operation(summary = "내 AI 메모리 조회", description = "현재 로그인 사용자의 RAG 개인화 메모리를 조회합니다.")
    public ApiResponse<UserAiMemoryResponse> getMyMemory(@CurrentUser Long userId) {
        return ApiResponse.success(memoryService.find(userId)
                .map(UserAiMemoryResponse::from)
                .orElseGet(UserAiMemoryResponse::empty));
    }

    @GetMapping("/me/events")
    @Operation(summary = "내 AI 메모리 이벤트 조회", description = "현재 로그인 사용자의 최근 RAG 조회/질문 이벤트를 조회합니다.")
    public ApiResponse<List<UserAiMemoryEventResponse>> getMyMemoryEvents(
            @CurrentUser Long userId,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(memoryService.findEvents(userId, limit)
                .stream()
                .map(UserAiMemoryEventResponse::from)
                .toList());
    }

    @PutMapping("/me")
    @Operation(summary = "내 AI 메모리 수정", description = "현재 로그인 사용자의 관심 지역과 선호 가격대를 직접 수정합니다.")
    public ApiResponse<UserAiMemoryResponse> updateMyMemory(
            @CurrentUser Long userId,
            @RequestBody UserAiMemoryUpdateRequest request
    ) {
        return ApiResponse.success(UserAiMemoryResponse.from(memoryService.updatePreference(
                userId,
                request.preferredRegion(),
                request.minPrice(),
                request.maxPrice()
        )));
    }

    @DeleteMapping("/me")
    @Operation(summary = "내 AI 메모리 초기화", description = "현재 로그인 사용자의 AI 메모리와 조회 이벤트를 모두 삭제합니다.")
    public ApiResponse<Void> clearMyMemory(@CurrentUser Long userId) {
        memoryService.clear(userId);
        return ApiResponse.empty();
    }
}
