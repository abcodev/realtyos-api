package realestate.server.application.rag.interfaces.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RagSearchRequest(
        @NotBlank(message = "검색어는 비어 있을 수 없습니다.")
        String query,

        @Min(value = 1, message = "topK는 1 이상이어야 합니다.")
        @Max(value = 20, message = "topK는 20 이하여야 합니다.")
        Integer topK,

        String embeddingProvider,

        String embeddingModel
) {
}
