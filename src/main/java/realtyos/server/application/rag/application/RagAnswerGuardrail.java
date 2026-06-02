package realtyos.server.application.rag.application;

import org.springframework.stereotype.Component;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class RagAnswerGuardrail {

    private static final String NO_MATCHING_EVIDENCE = "일치하는 근거를 찾지 못했습니다.";

    public boolean hasUsableEvidence(RagSearchCondition condition, List<RagSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().anyMatch(result -> matchesCondition(condition, result));
    }

    public String finalizeAnswer(String answer, List<RagSearchResult> results) {
        if (answer == null || answer.isBlank()) {
            return buildEvidenceSummary(results);
        }
        if (answer.contains("일치하는 근거를 찾지 못했습니다") && results != null && !results.isEmpty()) {
            return buildEvidenceSummary(results);
        }
        if (answer.contains("문서에 없는") && results != null && !results.isEmpty()) {
            return buildEvidenceSummary(results);
        }
        return answer;
    }

    public String noMatchingEvidenceMessage() {
        return NO_MATCHING_EVIDENCE;
    }

    public boolean shouldUseEvidenceSummary(String query) {
        return hasText(query) && containsAny(query, "비교", "차이", "대비", " vs ", "VS");
    }

    public String buildEvidenceSummary(List<RagSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return NO_MATCHING_EVIDENCE;
        }
        Map<String, List<RagSearchResult>> groupedByRegion = results.stream()
                .collect(Collectors.groupingBy(
                        result -> safe(result.region()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        if (groupedByRegion.size() > 1) {
            return buildComparisonSummary(groupedByRegion);
        }
        return buildFlatEvidenceSummary(results);
    }

    private boolean matchesCondition(RagSearchCondition condition, RagSearchResult result) {
        if (condition == null) {
            return true;
        }
        if (condition.minPrice() != null && (result.dealAmount() == null || result.dealAmount() < condition.minPrice())) {
            return false;
        }
        if (condition.maxPrice() != null && (result.dealAmount() == null || result.dealAmount() > condition.maxPrice())) {
            return false;
        }
        Double area = parseArea(result.exclusiveArea());
        if (condition.minArea() != null && (area == null || area < condition.minArea())) {
            return false;
        }
        if (condition.maxArea() != null && (area == null || area > condition.maxArea())) {
            return false;
        }
        if (hasText(condition.apartmentName()) && !contains(result.apartmentName(), condition.apartmentName())) {
            return false;
        }
        if (hasText(condition.region()) && !matchesRegion(condition.region(), result)) {
            return false;
        }
        return true;
    }

    private boolean matchesRegion(String region, RagSearchResult result) {
        String normalizedRegion = region.trim();
        if (normalizedRegion.equals("강남") || normalizedRegion.equals("강남구")) {
            return contains(result.region(), "11680");
        }
        if (normalizedRegion.equals("대치")) {
            return contains(result.region(), "대치동");
        }
        if (normalizedRegion.equals("잠실")) {
            return contains(result.region(), "잠실동");
        }
        if (normalizedRegion.equals("개포")) {
            return contains(result.region(), "개포동");
        }
        return contains(result.region(), normalizedRegion)
                || contains(result.apartmentName(), normalizedRegion);
    }

    private String buildFlatEvidenceSummary(List<RagSearchResult> results) {
        StringBuilder summary = new StringBuilder();
        summary.append("검색된 실거래 근거 기준으로 요약하면 다음과 같습니다.\n\n");
        results.stream().limit(5).forEach(result -> summary.append("- ")
                .append(safe(result.apartmentName())).append(" / ")
                .append(safe(result.region())).append(" / ")
                .append(safe(result.dealDate())).append(" / ")
                .append(result.dealAmount() == null ? "금액 정보없음" : result.dealAmount().toString() + "만원").append(" / ")
                .append(safe(result.exclusiveArea())).append("㎡")
                .append("\n"));
        summary.append("\n위 내용은 제공된 RAG 근거 문서에 있는 거래만 기준으로 작성했습니다.");
        return summary.toString();
    }

    private String buildComparisonSummary(Map<String, List<RagSearchResult>> groupedByRegion) {
        StringBuilder summary = new StringBuilder();
        summary.append("검색된 실거래 근거 기준 비교입니다.\n\n");
        summary.append("| 지역 | 근거건수 | 가격 범위 | 평균 거래금액 | 면적 범위 | 최근 거래 |\n");
        summary.append("|---|---:|---:|---:|---:|---|\n");

        groupedByRegion.forEach((region, results) -> {
            List<Long> prices = results.stream()
                    .map(RagSearchResult::dealAmount)
                    .filter(Objects::nonNull)
                    .toList();
            List<Double> areas = results.stream()
                    .map(result -> parseArea(result.exclusiveArea()))
                    .filter(Objects::nonNull)
                    .toList();
            RagSearchResult latest = results.stream()
                    .max(Comparator.comparing(result -> safe(result.dealDate())))
                    .orElse(results.getFirst());

            summary.append("| ")
                    .append(region)
                    .append(" | ")
                    .append(results.size())
                    .append(" | ")
                    .append(formatPriceRange(prices))
                    .append(" | ")
                    .append(formatAveragePrice(prices))
                    .append(" | ")
                    .append(formatAreaRange(areas))
                    .append(" | ")
                    .append(safe(latest.apartmentName()))
                    .append(" ")
                    .append(safe(latest.dealDate()))
                    .append(" |")
                    .append("\n");
        });

        summary.append("\n근거 거래:\n");
        groupedByRegion.forEach((region, results) ->
                results.stream().limit(3).forEach(result -> summary.append("- ")
                        .append(region).append(" / ")
                        .append(safe(result.apartmentName())).append(" / ")
                        .append(safe(result.dealDate())).append(" / ")
                        .append(formatPrice(result.dealAmount())).append(" / ")
                        .append(safe(result.exclusiveArea())).append("㎡")
                        .append("\n")));
        summary.append("\n위 비교는 제공된 RAG 근거 문서에 있는 거래만 기준으로 작성했습니다.");
        return summary.toString();
    }

    private boolean contains(String source, String keyword) {
        if (!hasText(source) || !hasText(keyword)) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Double parseArea(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safe(String value) {
        return hasText(value) ? value : "정보없음";
    }

    private String formatPriceRange(List<Long> prices) {
        if (prices.isEmpty()) {
            return "정보없음";
        }
        long min = prices.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = prices.stream().mapToLong(Long::longValue).max().orElse(0);
        return formatPrice(min) + " ~ " + formatPrice(max);
    }

    private String formatAveragePrice(List<Long> prices) {
        if (prices.isEmpty()) {
            return "정보없음";
        }
        long average = Math.round(prices.stream().mapToLong(Long::longValue).average().orElse(0));
        return formatPrice(average);
    }

    private String formatPrice(Long price) {
        if (price == null) {
            return "정보없음";
        }
        long eok = price / 10000;
        long manwon = price % 10000;
        if (eok > 0 && manwon > 0) {
            return eok + "억 " + manwon + "만원";
        }
        if (eok > 0) {
            return eok + "억원";
        }
        return price + "만원";
    }

    private String formatAreaRange(List<Double> areas) {
        if (areas.isEmpty()) {
            return "정보없음";
        }
        double min = areas.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = areas.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return "%.2f㎡ ~ %.2f㎡".formatted(min, max);
    }
}
