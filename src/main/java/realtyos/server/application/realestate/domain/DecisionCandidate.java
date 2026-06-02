package realtyos.server.application.realestate.domain;

import java.util.List;

public record DecisionCandidate(
        String apartmentName,
        String regionCode,
        String dongName,
        String latestDealDate,
        Long latestDealAmount,
        Long minDealAmount,
        Long maxDealAmount,
        Long averageDealAmount,
        Double minExclusiveArea,
        Double maxExclusiveArea,
        Double averageExclusiveArea,
        Long dealCount,
        Long averagePricePerPyeong,
        double score,
        DecisionScoreBreakdown scoreBreakdown,
        List<String> strengths,
        List<String> cautions,
        List<DecisionDealSample> samples
) {
}
