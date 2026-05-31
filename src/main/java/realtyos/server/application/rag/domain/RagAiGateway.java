package realtyos.server.application.rag.domain;

import java.util.function.Consumer;

public interface RagAiGateway {

    String askRouted(String entityType, String userMessage, String requestedProvider, String requestedModel);

    RagAiRoute route(String entityType, String userMessage, String requestedProvider, String requestedModel);

    void stream(RagAiRoute route, String entityType, String userMessage, Consumer<String> onChunk);
}
