package realtyos.server.application.rag.memory;

import realtyos.server.application.rag.domain.RagSearchCondition;

public record UserAiMemory(
        Long userId,
        String preferredRegion,
        String recentRegion,
        Long minPrice,
        Long maxPrice,
        long queryCount,
        String lastQuery
) {

    public RagSearchCondition mergeInto(RagSearchCondition condition) {
        RagSearchCondition base = condition == null
                ? new RagSearchCondition(null, null, null, null, null, null, null, null, null, null, null)
                : condition;

        return new RagSearchCondition(
                firstText(base.region(), preferredRegion, recentRegion),
                base.apartmentName(),
                base.fromYear(),
                base.fromMonth(),
                base.toYear(),
                base.toMonth(),
                base.minPrice() != null ? base.minPrice() : minPrice,
                base.maxPrice() != null ? base.maxPrice() : maxPrice,
                base.minArea(),
                base.maxArea(),
                base.recentFirst()
        );
    }

    public String toPromptContext() {
        if (isEmpty()) {
            return "저장된 사용자 메모리가 없습니다.";
        }
        return """
                관심 지역: %s
                최근 조회 지역: %s
                선호 가격대: %s
                누적 RAG 조회 수: %d
                마지막 질문: %s
                """.formatted(
                valueOrUnknown(preferredRegion),
                valueOrUnknown(recentRegion),
                priceRangeText(),
                queryCount,
                valueOrUnknown(lastQuery)
        ).trim();
    }

    private boolean isEmpty() {
        return preferredRegion == null
                && recentRegion == null
                && minPrice == null
                && maxPrice == null
                && lastQuery == null;
    }

    private String priceRangeText() {
        if (minPrice == null && maxPrice == null) {
            return "정보없음";
        }
        if (minPrice != null && maxPrice != null) {
            return "%s만원 ~ %s만원".formatted(minPrice, maxPrice);
        }
        if (minPrice != null) {
            return "%s만원 이상".formatted(minPrice);
        }
        return "%s만원 이하".formatted(maxPrice);
    }

    private String firstText(String first, String second, String third) {
        if (hasText(first)) {
            return first;
        }
        if (hasText(second)) {
            return second;
        }
        return hasText(third) ? third : null;
    }

    private String valueOrUnknown(String value) {
        return hasText(value) ? value : "정보없음";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
