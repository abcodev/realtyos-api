package realtyos.server.application.rag.application;

public record RagAnswerRoute(
        RagAnswerRouteType type,
        String reason
) {
    public boolean usesDecisionEngine() {
        return type != RagAnswerRouteType.SEARCH;
    }
}
