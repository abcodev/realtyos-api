package realtyos.server.application.realestate.domain;

public record DecisionTargetSummary(
        String name,
        int candidateCount,
        Long dealCount,
        String latestDealDate,
        Long latestDealAmount,
        Long averageDealAmount,
        Long medianDealAmount,
        Long minDealAmount,
        Long maxDealAmount,
        Long averagePricePerPyeong,
        Double threeMonthChangeRate
) {
}
