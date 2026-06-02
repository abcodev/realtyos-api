package realtyos.server.application.rag.application;

import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionResult;

import java.util.List;

public final class DecisionAnswerSourceMapper {

    private DecisionAnswerSourceMapper() {
    }

    public static List<RagAnswerSource> from(DecisionResult result) {
        return result.candidates().stream()
                .flatMap(candidate -> candidate.samples().stream())
                .map(DecisionAnswerSourceMapper::from)
                .toList();
    }

    private static RagAnswerSource from(DecisionDealSample sample) {
        return new RagAnswerSource(
                null,
                "%s %s 아파트 실거래 %s".formatted(sample.apartmentName(), sample.dongName(), sample.dealDate()),
                sample.apartmentName(),
                "%s %s".formatted(sample.regionCode(), sample.dongName()),
                "DEAL",
                sample.dealId(),
                "DECISION",
                sample.dealDate(),
                sample.exclusiveArea() == null ? null : sample.exclusiveArea().toString(),
                sample.dealAmount(),
                sample.floor(),
                null,
                0.0,
                1.0,
                1.0
        );
    }
}
