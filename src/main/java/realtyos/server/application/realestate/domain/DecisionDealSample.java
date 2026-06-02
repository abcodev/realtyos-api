package realtyos.server.application.realestate.domain;

public record DecisionDealSample(
        Long dealId,
        String apartmentName,
        String regionCode,
        String dongName,
        String dealDate,
        Long dealAmount,
        Double exclusiveArea,
        String floor
) {
}
