package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import realestate.server.application.rag.domain.EmbeddingClient;
import realestate.server.application.rag.domain.EmbeddingModelProfile;
import realestate.server.application.rag.domain.EmbeddingProvider;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmbeddingClientRegistry {

    private static final EmbeddingProvider DEFAULT_PROVIDER = EmbeddingProvider.OPENAI;

    private final List<EmbeddingClient> clients;

    public EmbeddingClient resolve(EmbeddingProvider provider) {
        return clients.stream()
                .filter(client -> client.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 embedding provider입니다: " + provider));
    }

    public EmbeddingModelProfile resolveProfile(String provider, String model) {
        EmbeddingProvider resolvedProvider = resolveProvider(provider);
        EmbeddingClient client = resolve(resolvedProvider);
        String resolvedModel = StringUtils.hasText(model) ? model : client.defaultModel();
        return new EmbeddingModelProfile(resolvedProvider, resolvedModel);
    }

    private EmbeddingProvider resolveProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return DEFAULT_PROVIDER;
        }
        return EmbeddingProvider.valueOf(provider.trim().toUpperCase());
    }
}
