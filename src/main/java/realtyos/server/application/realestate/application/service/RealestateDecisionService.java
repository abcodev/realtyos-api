package realtyos.server.application.realestate.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagQueryRewritePolicy;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RealestateDecisionService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final Pattern COMPARISON_SPLIT_PATTERN = Pattern.compile("\\s*(?:이랑|랑|하고|와|과|vs|VS|비교)\\s*");
    private static final Pattern ADMIN_REGION_PATTERN = Pattern.compile("([가-힣]{2,}(?:구|동|읍|면|리))");

    private final DealAnalysisService dealAnalysisService;
    private final DecisionScoreService decisionScoreService;
    private final RagQueryRewritePolicy queryRewritePolicy = new RagQueryRewritePolicy();

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return containsAny(query, "추천", "후보", "의사결정", "살만", "매수", "투자", "실거주", "비교", "골라", "어디", "중",
                "시세", "흐름", "어때", "어떤가", "괜찮", "나아");
    }

    public DecisionResult decide(String query, Integer limit, RagSearchCondition explicitCondition) {
        List<ComparisonTarget> comparisonTargets = inferComparisonTargets(query);
        List<String> comparisonRegions = queryRewritePolicy.inferComparisonRegions(query);
        boolean hasComparisonTargets = !comparisonTargets.isEmpty();
        RagSearchCondition condition = queryRewritePolicy
                .rewrite(query, !hasComparisonTargets && comparisonRegions.isEmpty() ? explicitCondition : withoutRegionAndApartment(explicitCondition))
                .condition();
        if (condition.region() == null && containsSeoul(query)) {
            condition = withRegion(condition, "서울");
        }
        RagSearchCondition resolvedCondition = condition;

        List<DecisionCandidate> rawCandidates = hasComparisonTargets
                ? findComparisonTargetCandidates(comparisonTargets, resolvedCondition, normalizeLimit(limit))
                : comparisonRegions.isEmpty()
                ? dealAnalysisService.findCandidates(resolvedCondition, normalizeLimit(limit))
                : findComparisonCandidates(comparisonRegions, resolvedCondition, normalizeLimit(limit));
        List<DecisionCandidate> scoredCandidates = rawCandidates.stream()
                .map(candidate -> decisionScoreService.score(candidate, resolvedCondition))
                .sorted(Comparator.comparing(DecisionCandidate::score).reversed()
                        .thenComparing(DecisionCandidate::dealCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<DecisionCandidate> candidates = hasComparisonTargets
                ? balanceComparisonTargetCandidates(scoredCandidates, comparisonTargets, normalizeLimit(limit))
                : comparisonRegions.isEmpty()
                ? scoredCandidates
                : balanceComparisonCandidates(scoredCandidates, comparisonRegions, normalizeLimit(limit));

        return new DecisionResult(
                query,
                resolvedCondition,
                buildSummary(candidates, comparisonTargets),
                comparisonTargets.stream().map(ComparisonTarget::value).toList(),
                candidates
        );
    }

    private List<DecisionCandidate> findComparisonCandidates(
            List<String> comparisonRegions,
            RagSearchCondition baseCondition,
            int limit
    ) {
        Map<String, DecisionCandidate> merged = new LinkedHashMap<>();
        int perRegionLimit = Math.max(3, Math.min(8, limit));
        for (String region : comparisonRegions) {
            RagSearchCondition regionCondition = withRegion(baseCondition, region);
            dealAnalysisService.findCandidates(regionCondition, perRegionLimit)
                    .forEach(candidate -> merged.putIfAbsent(candidateKey(candidate), candidate));
        }
        return new ArrayList<>(merged.values());
    }

    private List<DecisionCandidate> findComparisonTargetCandidates(
            List<ComparisonTarget> targets,
            RagSearchCondition baseCondition,
            int limit
    ) {
        Map<String, DecisionCandidate> merged = new LinkedHashMap<>();
        int perTargetLimit = Math.max(3, Math.min(8, limit));
        for (ComparisonTarget target : targets) {
            findTargetCandidates(target, baseCondition, perTargetLimit)
                    .forEach(candidate -> merged.putIfAbsent(candidateKey(candidate), candidate));
        }
        return new ArrayList<>(merged.values());
    }

    private List<DecisionCandidate> findTargetCandidates(
            ComparisonTarget target,
            RagSearchCondition baseCondition,
            int limit
    ) {
        if (target.kind() != TargetKind.ANY) {
            return dealAnalysisService.findCandidates(target.toCondition(baseCondition), limit);
        }

        List<DecisionCandidate> regionCandidates = dealAnalysisService.findCandidates(
                target.regionCondition(baseCondition),
                limit
        );
        if (!regionCandidates.isEmpty()) {
            return regionCandidates;
        }
        return dealAnalysisService.findCandidates(target.apartmentCondition(baseCondition), limit);
    }

    private List<DecisionCandidate> balanceComparisonCandidates(
            List<DecisionCandidate> candidates,
            List<String> comparisonRegions,
            int limit
    ) {
        LinkedHashMap<String, DecisionCandidate> balanced = new LinkedHashMap<>();
        int perRegionLimit = Math.max(2, Math.min(3, limit));

        for (String region : comparisonRegions) {
            candidates.stream()
                    .filter(candidate -> matchesRegion(candidate, region))
                    .limit(perRegionLimit)
                    .forEach(candidate -> balanced.putIfAbsent(candidateKey(candidate), candidate));
        }

        candidates.stream()
                .limit(limit)
                .forEach(candidate -> balanced.putIfAbsent(candidateKey(candidate), candidate));

        return new ArrayList<>(balanced.values()).stream()
                .limit(limit)
                .toList();
    }

    private boolean matchesRegion(DecisionCandidate candidate, String region) {
        String dongName = candidate.dongName() == null ? "" : candidate.dongName();
        return dongName.contains(region) || region.contains(dongName);
    }

    private List<DecisionCandidate> balanceComparisonTargetCandidates(
            List<DecisionCandidate> candidates,
            List<ComparisonTarget> targets,
            int limit
    ) {
        LinkedHashMap<String, DecisionCandidate> balanced = new LinkedHashMap<>();
        int perTargetLimit = Math.max(2, Math.min(3, limit));

        for (ComparisonTarget target : targets) {
            candidates.stream()
                    .filter(candidate -> target.matches(candidate))
                    .limit(perTargetLimit)
                    .forEach(candidate -> balanced.putIfAbsent(candidateKey(candidate), candidate));
        }

        return new ArrayList<>(balanced.values()).stream()
                .limit(limit)
                .toList();
    }

    private String candidateKey(DecisionCandidate candidate) {
        return "%s:%s:%s".formatted(candidate.regionCode(), candidate.dongName(), candidate.apartmentName());
    }

    public String formatAnswer(DecisionResult result) {
        if (result.candidates().isEmpty()) {
            return result.summary();
        }
        if (result.comparisonTargets().size() > 1) {
            return formatComparisonAnswer(result);
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

    private String formatComparisonAnswer(DecisionResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append(result.summary()).append("\n\n");
        answer.append("| 비교대상 | 대표 후보 | 최고점 | 평균가 | 평당가 | 거래건수 |\n");
        answer.append("|---|---|---:|---:|---:|---:|\n");

        for (String target : result.comparisonTargets()) {
            List<DecisionCandidate> targetCandidates = candidatesForTarget(result.candidates(), target);
            if (targetCandidates.isEmpty()) {
                answer.append("| ").append(target).append(" | 근거 없음 | - | - | - | 0 |\n");
                continue;
            }
            DecisionCandidate best = targetCandidates.getFirst();
            answer.append("| ")
                    .append(target)
                    .append(" | ")
                    .append(best.dongName()).append(" ").append(best.apartmentName())
                    .append(" | ")
                    .append(best.score())
                    .append(" | ")
                    .append(formatPrice(best.averageDealAmount()))
                    .append(" | ")
                    .append(formatPrice(best.averagePricePerPyeong()))
                    .append(" | ")
                    .append(best.dealCount() == null ? 0 : best.dealCount())
                    .append(" |\n");
        }

        answer.append("\n대상별 후보:\n");
        for (String target : result.comparisonTargets()) {
            List<DecisionCandidate> targetCandidates = candidatesForTarget(result.candidates(), target);
            answer.append("\n").append(target).append("\n");
            if (targetCandidates.isEmpty()) {
                answer.append("- 조건에 맞는 최근 실거래 후보를 찾지 못했습니다.\n");
                continue;
            }
            targetCandidates.stream().limit(3).forEach(candidate -> answer.append("- ")
                    .append(candidate.dongName()).append(" ").append(candidate.apartmentName())
                    .append(": 점수 ").append(candidate.score())
                    .append(", 최근 ").append(safe(candidate.latestDealDate()))
                    .append(" / ").append(formatPrice(candidate.latestDealAmount()))
                    .append(", 평균 ").append(formatPrice(candidate.averageDealAmount()))
                    .append(", 거래 ").append(candidate.dealCount() == null ? 0 : candidate.dealCount()).append("건")
                    .append("\n"));
        }

        answer.append("\n판단 포인트:\n");
        for (String target : result.comparisonTargets()) {
            List<DecisionCandidate> targetCandidates = candidatesForTarget(result.candidates(), target);
            if (targetCandidates.isEmpty()) {
                continue;
            }
            DecisionCandidate best = targetCandidates.getFirst();
            answer.append("- ").append(target).append(": ")
                    .append(best.dongName()).append(" ").append(best.apartmentName())
                    .append(" 기준으로 ")
                    .append(String.join(" ", best.strengths()));
            if (!best.cautions().isEmpty()) {
                answer.append(" 주의: ").append(String.join(" ", best.cautions()));
            }
            answer.append("\n");
        }

        answer.append("\n근거 거래:\n");
        for (String target : result.comparisonTargets()) {
            candidatesForTarget(result.candidates(), target).stream().limit(2)
                    .flatMap(candidate -> candidate.samples().stream().limit(1))
                    .forEach(sample -> answer.append("- ").append(target).append(" / ").append(formatSample(sample)).append("\n"));
        }
        answer.append("\n위 비교는 최근 실거래 데이터의 예산 적합도, 면적 적합도, 거래건수, 최근성을 함께 점수화한 의사결정 보조 결과입니다.");
        return answer.toString();
    }

    private List<DecisionCandidate> candidatesForTarget(List<DecisionCandidate> candidates, String target) {
        return candidates.stream()
                .filter(candidate -> {
                    String dongName = candidate.dongName() == null ? "" : candidate.dongName();
                    String apartmentName = candidate.apartmentName() == null ? "" : candidate.apartmentName();
                    return dongName.contains(target)
                            || target.contains(dongName)
                            || apartmentName.contains(target)
                            || target.contains(apartmentName);
                })
                .toList();
    }

    private String buildSummary(List<DecisionCandidate> candidates, List<ComparisonTarget> comparisonTargets) {
        if (candidates.isEmpty()) {
            return "조건에 맞는 최근 실거래 후보를 찾지 못했습니다.";
        }
        if (comparisonTargets.size() > 1) {
            return "%s 비교 결과입니다. 각 대상별 최근 실거래 후보를 분리해 점수화했습니다."
                    .formatted(String.join(" / ", comparisonTargets.stream().map(ComparisonTarget::value).toList()));
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

    private RagSearchCondition withoutRegionAndApartment(RagSearchCondition condition) {
        if (condition == null) {
            return null;
        }
        return new RagSearchCondition(
                null,
                null,
                condition.fromYear(),
                condition.fromMonth(),
                condition.toYear(),
                condition.toMonth(),
                condition.minPrice(),
                condition.maxPrice(),
                condition.minArea(),
                condition.maxArea(),
                condition.recentFirst()
        );
    }

    private List<ComparisonTarget> inferComparisonTargets(String query) {
        if (query == null || !containsAny(query, "비교", "차이", "대비", " vs ", "VS", "와", "과", "랑", "하고", "이랑")) {
            return List.of();
        }
        List<ComparisonTarget> regionTargets = inferAdministrativeRegionTargets(query);
        if (regionTargets.size() > 1) {
            return regionTargets;
        }

        String normalized = query
                .replace("시세", " ")
                .replace("어때", " ")
                .replace("어떤가", " ")
                .replace("좋을까", " ")
                .replace("좋아", " ")
                .replace("해줘", " ")
                .replace("해달라", " ")
                .replace("해달라고", " ")
                .replace("비교해줘", " ")
                .replace("비교해달라", " ")
                .replace("더", " ")
                .trim();
        List<ComparisonTarget> targets = new ArrayList<>();
        regionTargets.forEach(targets::add);
        for (String token : COMPARISON_SPLIT_PATTERN.split(normalized)) {
            for (String part : token.split("\\s+")) {
                String value = part.replaceAll("[^가-힣A-Za-z0-9()\\-]", "").trim();
                if (value.isBlank() || value.length() < 2 || value.equals("중") || value.equals("어디")
                        || value.equals("비교") || value.equals("해줘") || value.equals("해달라")) {
                    continue;
                }
                if (targets.stream().anyMatch(target -> target.value().equals(value))) {
                    continue;
                }
                targets.add(ComparisonTarget.from(value));
            }
        }
        return targets.size() > 1 ? targets : List.of();
    }

    private List<ComparisonTarget> inferAdministrativeRegionTargets(String query) {
        LinkedHashMap<String, ComparisonTarget> targets = new LinkedHashMap<>();
        Matcher matcher = ADMIN_REGION_PATTERN.matcher(query);
        while (matcher.find()) {
            String region = matcher.group(1);
            targets.putIfAbsent(region, new ComparisonTarget(region, TargetKind.REGION));
        }
        return new ArrayList<>(targets.values());
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

    private enum TargetKind {
        REGION,
        APARTMENT,
        ANY
    }

    private record ComparisonTarget(String value, TargetKind kind) {

        static ComparisonTarget from(String value) {
            return new ComparisonTarget(value, isRegionValue(value) ? TargetKind.REGION : TargetKind.ANY);
        }

        RagSearchCondition toCondition(RagSearchCondition base) {
            return kind == TargetKind.REGION ? regionCondition(base) : apartmentCondition(base);
        }

        RagSearchCondition regionCondition(RagSearchCondition base) {
            return new RagSearchCondition(
                    value,
                    base == null ? null : base.apartmentName(),
                    base == null ? null : base.fromYear(),
                    base == null ? null : base.fromMonth(),
                    base == null ? null : base.toYear(),
                    base == null ? null : base.toMonth(),
                    base == null ? null : base.minPrice(),
                    base == null ? null : base.maxPrice(),
                    base == null ? null : base.minArea(),
                    base == null ? null : base.maxArea(),
                    base == null ? null : base.recentFirst()
            );
        }

        RagSearchCondition apartmentCondition(RagSearchCondition base) {
            return new RagSearchCondition(
                    base == null ? null : base.region(),
                    value,
                    base == null ? null : base.fromYear(),
                    base == null ? null : base.fromMonth(),
                    base == null ? null : base.toYear(),
                    base == null ? null : base.toMonth(),
                    base == null ? null : base.minPrice(),
                    base == null ? null : base.maxPrice(),
                    base == null ? null : base.minArea(),
                    base == null ? null : base.maxArea(),
                    base == null ? null : base.recentFirst()
            );
        }

        boolean matches(DecisionCandidate candidate) {
            String dongName = candidate.dongName() == null ? "" : candidate.dongName();
            String apartmentName = candidate.apartmentName() == null ? "" : candidate.apartmentName();
            if (kind == TargetKind.REGION) {
                return dongName.contains(value) || value.contains(dongName);
            }
            if (kind == TargetKind.APARTMENT) {
                return apartmentName.contains(value) || value.contains(apartmentName);
            }
            return dongName.contains(value)
                    || value.contains(dongName)
                    || apartmentName.contains(value)
                    || value.contains(apartmentName);
        }

        private static boolean isRegionValue(String value) {
            return value.endsWith("구")
                    || value.endsWith("동")
                    || value.endsWith("읍")
                    || value.endsWith("면")
                    || value.endsWith("리");
        }
    }
}
