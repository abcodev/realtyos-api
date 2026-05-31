package realestate.server.application.rag.domain;

public record RagQueryRewriteResult(
        String rewrittenQuery,
        RagSearchCondition condition
) {
}
