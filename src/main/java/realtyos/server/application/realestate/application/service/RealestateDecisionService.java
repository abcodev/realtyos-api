package realtyos.server.application.realestate.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagQueryRewritePolicy;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.realestate.domain.DecisionCandidate;
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
    private final DecisionTargetSummaryBuilder targetSummaryBuilder;
    private final DecisionResultFormatter decisionResultFormatter;
    private final RagQueryRewritePolicy queryRewritePolicy = new RagQueryRewritePolicy();

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
                targetSummaryBuilder.build(candidates, comparisonTargets.stream().map(ComparisonTarget::value).toList()),
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
        return decisionResultFormatter.format(result);
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
        if (isSingleRegionCandidateComparison(query)) {
            return List.of();
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
                .replace("갈아타기", " ")
                .replace("후보를", " ")
                .replace("후보", " ")
                .replace("추천", " ")
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

    private boolean isSingleRegionCandidateComparison(String query) {
        if (query == null) {
            return false;
        }
        boolean candidateIntent = containsAny(query, "후보", "추천", "갈아타기");
        boolean explicitPairConnector = containsAny(query, " vs ", "VS", "와", "과", "랑", "하고", "이랑");
        return candidateIntent && !explicitPairConnector;
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
