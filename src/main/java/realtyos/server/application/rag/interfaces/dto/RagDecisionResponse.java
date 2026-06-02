package realtyos.server.application.rag.interfaces.dto;

import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionResult;
import realtyos.server.application.realestate.domain.DecisionScoreBreakdown;

import java.util.List;

public record RagDecisionResponse(
        String type,
        String summary,
        String winner,
        List<RagDecisionTargetResponse> targets
) {
    public static RagDecisionResponse from(DecisionResult result) {
        if (result == null) {
            return null;
        }
        List<String> targetNames = result.comparisonTargets().isEmpty()
                ? List.of("추천 후보")
                : result.comparisonTargets();
        List<RagDecisionTargetResponse> targets = targetNames.stream()
                .map(target -> RagDecisionTargetResponse.from(target, candidatesForTarget(result, target)))
                .toList();
        return new RagDecisionResponse(
                result.comparisonTargets().size() > 1 ? "COMPARISON" : "RECOMMENDATION",
                result.summary(),
                targets.stream()
                        .filter(target -> target.bestCandidate() != null)
                        .findFirst()
                        .map(RagDecisionTargetResponse::name)
                        .orElse(null),
                targets
        );
    }

    private static List<DecisionCandidate> candidatesForTarget(DecisionResult result, String target) {
        if (result.comparisonTargets().isEmpty()) {
            return result.candidates();
        }
        return result.candidates().stream()
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

    public record RagDecisionTargetResponse(
            String name,
            int candidateCount,
            Long averageDealAmount,
            Long averagePricePerPyeong,
            Long dealCount,
            Double bestScore,
            RagDecisionCandidateResponse bestCandidate,
            List<RagDecisionCandidateResponse> candidates
    ) {
        private static RagDecisionTargetResponse from(String name, List<DecisionCandidate> candidates) {
            Long averageDealAmount = averageLong(candidates.stream()
                    .map(DecisionCandidate::averageDealAmount)
                    .toList());
            Long averagePricePerPyeong = averageLong(candidates.stream()
                    .map(DecisionCandidate::averagePricePerPyeong)
                    .toList());
            Long dealCount = candidates.stream()
                    .map(DecisionCandidate::dealCount)
                    .filter(value -> value != null)
                    .mapToLong(Long::longValue)
                    .sum();
            List<RagDecisionCandidateResponse> candidateResponses = candidates.stream()
                    .map(RagDecisionCandidateResponse::from)
                    .toList();
            RagDecisionCandidateResponse bestCandidate = candidateResponses.isEmpty() ? null : candidateResponses.getFirst();
            return new RagDecisionTargetResponse(
                    name,
                    candidates.size(),
                    averageDealAmount,
                    averagePricePerPyeong,
                    dealCount,
                    bestCandidate == null ? null : bestCandidate.score(),
                    bestCandidate,
                    candidateResponses
            );
        }

        private static Long averageLong(List<Long> values) {
            return Math.round(values.stream()
                    .filter(value -> value != null)
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0));
        }
    }

    public record RagDecisionCandidateResponse(
            String apartmentName,
            String regionCode,
            String dongName,
            String latestDealDate,
            Long latestDealAmount,
            Long averageDealAmount,
            Long averagePricePerPyeong,
            Long dealCount,
            Double minExclusiveArea,
            Double maxExclusiveArea,
            Double averageExclusiveArea,
            double score,
            DecisionScoreBreakdown scoreBreakdown,
            List<String> strengths,
            List<String> cautions,
            List<RagDecisionDealSampleResponse> samples
    ) {
        private static RagDecisionCandidateResponse from(DecisionCandidate candidate) {
            return new RagDecisionCandidateResponse(
                    candidate.apartmentName(),
                    candidate.regionCode(),
                    candidate.dongName(),
                    candidate.latestDealDate(),
                    candidate.latestDealAmount(),
                    candidate.averageDealAmount(),
                    candidate.averagePricePerPyeong(),
                    candidate.dealCount(),
                    candidate.minExclusiveArea(),
                    candidate.maxExclusiveArea(),
                    candidate.averageExclusiveArea(),
                    candidate.score(),
                    candidate.scoreBreakdown(),
                    candidate.strengths(),
                    candidate.cautions(),
                    candidate.samples().stream()
                            .map(RagDecisionDealSampleResponse::from)
                            .toList()
            );
        }
    }

    public record RagDecisionDealSampleResponse(
            Long dealId,
            String apartmentName,
            String regionCode,
            String dongName,
            String dealDate,
            Long dealAmount,
            Double exclusiveArea,
            String floor
    ) {
        private static RagDecisionDealSampleResponse from(DecisionDealSample sample) {
            return new RagDecisionDealSampleResponse(
                    sample.dealId(),
                    sample.apartmentName(),
                    sample.regionCode(),
                    sample.dongName(),
                    sample.dealDate(),
                    sample.dealAmount(),
                    sample.exclusiveArea(),
                    sample.floor()
            );
        }
    }
}
