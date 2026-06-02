package realtyos.server.application.realestate.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagQueryRewritePolicy;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionResult;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RealestateDecisionService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final DealAnalysisService dealAnalysisService;
    private final DecisionScoreService decisionScoreService;
    private final RagQueryRewritePolicy queryRewritePolicy = new RagQueryRewritePolicy();

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return containsAny(query, "추천", "후보", "의사결정", "살만", "매수", "투자", "실거주", "비교", "골라", "어디", "중");
    }

    public DecisionResult decide(String query, Integer limit, RagSearchCondition explicitCondition) {
        RagSearchCondition condition = queryRewritePolicy.rewrite(query, explicitCondition).condition();
        if (condition.region() == null && containsSeoul(query)) {
            condition = withRegion(condition, "서울");
        }
        RagSearchCondition resolvedCondition = condition;

        List<DecisionCandidate> candidates = dealAnalysisService.findCandidates(resolvedCondition, normalizeLimit(limit)).stream()
                .map(candidate -> decisionScoreService.score(candidate, resolvedCondition))
                .sorted(Comparator.comparing(DecisionCandidate::score).reversed()
                        .thenComparing(DecisionCandidate::dealCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new DecisionResult(
                query,
                resolvedCondition,
                buildSummary(candidates),
                candidates
        );
    }

    public String formatAnswer(DecisionResult result) {
        if (result.candidates().isEmpty()) {
            return result.summary();
        }

        StringBuilder answer = new StringBuilder();
        answer.append(result.summary()).append("\n\n");
        answer.append("| 순위 | 후보 | 점수 | 최근 거래 | 평균가 | 평당가 | 거래건수 |\n");
        answer.append("|---:|---|---:|---|---:|---:|---:|\n");
        for (int i = 0; i < Math.min(5, result.candidates().size()); i++) {
            DecisionCandidate candidate = result.candidates().get(i);
            answer.append("| ")
                    .append(i + 1)
                    .append(" | ")
                    .append(candidate.dongName()).append(" ").append(candidate.apartmentName())
                    .append(" | ")
                    .append(candidate.score())
                    .append(" | ")
                    .append(safe(candidate.latestDealDate())).append(" / ").append(formatPrice(candidate.latestDealAmount()))
                    .append(" | ")
                    .append(formatPrice(candidate.averageDealAmount()))
                    .append(" | ")
                    .append(formatPrice(candidate.averagePricePerPyeong()))
                    .append(" | ")
                    .append(candidate.dealCount() == null ? 0 : candidate.dealCount())
                    .append(" |")
                    .append("\n");
        }

        answer.append("\n판단 포인트:\n");
        result.candidates().stream().limit(3).forEach(candidate -> {
            answer.append("- ")
                    .append(candidate.dongName()).append(" ").append(candidate.apartmentName())
                    .append(": ")
                    .append(String.join(" ", candidate.strengths()));
            if (!candidate.cautions().isEmpty()) {
                answer.append(" 주의: ").append(String.join(" ", candidate.cautions()));
            }
            answer.append("\n");
        });

        answer.append("\n근거 거래:\n");
        result.candidates().stream().limit(3)
                .flatMap(candidate -> candidate.samples().stream().limit(2))
                .forEach(sample -> answer.append("- ").append(formatSample(sample)).append("\n"));
        answer.append("\n위 결과는 최근 실거래 데이터의 예산 적합도, 면적 적합도, 거래건수, 최근성을 함께 점수화한 의사결정 보조 결과입니다.");
        return answer.toString();
    }

    private String buildSummary(List<DecisionCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "조건에 맞는 최근 실거래 후보를 찾지 못했습니다.";
        }
        DecisionCandidate best = candidates.getFirst();
        return "%s %s가 현재 조건에서 가장 높은 점수(%.1f점)를 받았습니다. 최근 거래, 예산 적합도, 면적 적합도, 거래 건수를 함께 고려한 결과입니다."
                .formatted(best.dongName(), best.apartmentName(), best.score());
    }

    private RagSearchCondition withRegion(RagSearchCondition condition, String region) {
        return new RagSearchCondition(
                region,
                condition == null ? null : condition.apartmentName(),
                condition == null ? null : condition.fromYear(),
                condition == null ? null : condition.fromMonth(),
                condition == null ? null : condition.toYear(),
                condition == null ? null : condition.toMonth(),
                condition == null ? null : condition.minPrice(),
                condition == null ? null : condition.maxPrice(),
                condition == null ? null : condition.minArea(),
                condition == null ? null : condition.maxArea(),
                condition == null ? null : condition.recentFirst()
        );
    }

    private boolean containsSeoul(String query) {
        return query != null && (query.contains("서울") || query.contains("서울시") || query.contains("서울특별시"));
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String formatSample(DecisionDealSample sample) {
        return "%s %s / %s / %s / %s㎡"
                .formatted(sample.dongName(), sample.apartmentName(), sample.dealDate(),
                        formatPrice(sample.dealAmount()), sample.exclusiveArea());
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "정보없음" : value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, limit);
    }
}
