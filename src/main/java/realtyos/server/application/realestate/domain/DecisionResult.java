package realtyos.server.application.realestate.domain;

import realtyos.server.application.rag.domain.RagSearchCondition;

import java.util.List;

public record DecisionResult(
        String query,
        RagSearchCondition condition,
        String summary,
        List<String> comparisonTargets,
        List<DecisionTargetSummary> targetSummaries,
        List<DecisionCandidate> candidates
) {
}
