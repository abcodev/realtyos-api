package realtyos.server.application.rag.domain;

public record RagAiRoute(
        String provider,
        String model,
        String fallbackProvider,
        String fallbackModel,
        String reason
) {
}
