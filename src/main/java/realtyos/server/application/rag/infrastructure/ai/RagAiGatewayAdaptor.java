package realtyos.server.application.rag.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realtyos.server.application.common.ai.AiProvider;
import realtyos.server.application.common.ai.AiService;
import realtyos.server.application.common.ai.routing.AiRoute;
import realtyos.server.application.rag.domain.RagAiGateway;
import realtyos.server.application.rag.domain.RagAiRoute;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class RagAiGatewayAdaptor implements RagAiGateway {

    private final AiService aiService;

    @Override
    public String askRouted(String entityType, String userMessage, String requestedProvider, String requestedModel) {
        return aiService.askRouted(entityType, userMessage, parseProvider(requestedProvider), requestedModel);
    }

    @Override
    public RagAiRoute route(String entityType, String userMessage, String requestedProvider, String requestedModel) {
        AiRoute route = aiService.route(entityType, userMessage, parseProvider(requestedProvider), requestedModel);
        return new RagAiRoute(
                route.provider().name(),
                route.model(),
                route.fallbackProvider() == null ? null : route.fallbackProvider().name(),
                route.fallbackModel(),
                route.reason()
        );
    }

    @Override
    public void stream(RagAiRoute route, String entityType, String userMessage, Consumer<String> onChunk) {
        aiService.stream(toCommonRoute(route), entityType, userMessage, onChunk);
    }

    private AiProvider parseProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return AiProvider.valueOf(provider.trim().toUpperCase());
    }

    private AiRoute toCommonRoute(RagAiRoute route) {
        return new AiRoute(
                AiProvider.valueOf(route.provider()),
                route.model(),
                route.fallbackProvider() == null ? null : AiProvider.valueOf(route.fallbackProvider()),
                route.fallbackModel(),
                route.reason()
        );
    }
}
