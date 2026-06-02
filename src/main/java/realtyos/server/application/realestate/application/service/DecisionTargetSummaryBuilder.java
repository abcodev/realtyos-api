package realtyos.server.application.realestate.application.service;

import org.springframework.stereotype.Component;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionTargetSummary;

import java.util.Comparator;
import java.util.List;

@Component
public class DecisionTargetSummaryBuilder {

    public List<DecisionTargetSummary> build(List<DecisionCandidate> candidates, List<String> targetNames) {
        if (targetNames != null && targetNames.size() > 1) {
            return targetNames.stream()
                    .map(target -> buildTargetSummary(target, candidatesForTarget(candidates, target)))
                    .toList();
        }
        return List.of(buildTargetSummary("추천 후보", candidates));
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

    private DecisionTargetSummary buildTargetSummary(String name, List<DecisionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new DecisionTargetSummary(name, 0, 0L, null, null, null, null, null, null, null, null);
        }

        List<Long> averagePrices = candidates.stream()
                .map(DecisionCandidate::averageDealAmount)
                .filter(value -> value != null)
                .sorted()
                .toList();
        DecisionCandidate latest = candidates.stream()
                .filter(candidate -> candidate.latestDealDate() != null)
                .max(Comparator.comparing(DecisionCandidate::latestDealDate))
                .orElse(candidates.getFirst());

        return new DecisionTargetSummary(
                name,
                candidates.size(),
                candidates.stream()
                        .map(DecisionCandidate::dealCount)
                        .filter(value -> value != null)
                        .mapToLong(Long::longValue)
                        .sum(),
                latest.latestDealDate(),
                latest.latestDealAmount(),
                averageLong(averagePrices),
                medianLong(averagePrices),
                candidates.stream()
                        .map(DecisionCandidate::minDealAmount)
                        .filter(value -> value != null)
                        .min(Long::compareTo)
                        .orElse(null),
                candidates.stream()
                        .map(DecisionCandidate::maxDealAmount)
                        .filter(value -> value != null)
                        .max(Long::compareTo)
                        .orElse(null),
                averageLong(candidates.stream()
                        .map(DecisionCandidate::averagePricePerPyeong)
                        .filter(value -> value != null)
                        .toList()),
                estimateRecentChangeRate(candidates)
        );
    }

    private Long averageLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private Long medianLong(List<Long> sortedValues) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return null;
        }
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return Math.round((sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0);
    }

    private Double estimateRecentChangeRate(List<DecisionCandidate> candidates) {
        List<DecisionDealSample> samples = candidates.stream()
                .flatMap(candidate -> candidate.samples().stream())
                .filter(sample -> sample.dealDate() != null && sample.dealAmount() != null)
                .sorted(Comparator.comparing(DecisionDealSample::dealDate))
                .toList();
        if (samples.size() < 2) {
            return null;
        }
        DecisionDealSample oldest = samples.getFirst();
        DecisionDealSample latest = samples.getLast();
        if (oldest.dealAmount() == null || oldest.dealAmount() == 0 || latest.dealAmount() == null) {
            return null;
        }
        return Math.round(((latest.dealAmount() - oldest.dealAmount()) * 1000.0 / oldest.dealAmount())) / 10.0;
    }
}
