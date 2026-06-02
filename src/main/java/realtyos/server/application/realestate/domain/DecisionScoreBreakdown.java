package realtyos.server.application.realestate.domain;

public record DecisionScoreBreakdown(
        double budget,
        double area,
        double liquidity,
        double recency
) {
}
