package realtyos.server.application.rag.domain;

import realtyos.server.application.realestate.domain.DecisionResult;

import java.util.List;

public record RagAnswer(
        String answer,
        List<RagAnswerSource> sources,
        DecisionResult decision
) {
    public RagAnswer(String answer, List<RagAnswerSource> sources) {
        this(answer, sources, null);
    }
}
