package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import realtyos.server.application.common.ai.config.AiConfig;
import realtyos.server.application.rag.domain.EmbeddingClient;
import realtyos.server.application.rag.domain.EmbeddingModelProfile;
import realtyos.server.application.rag.domain.EmbeddingProvider;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmbeddingClientRegistry {

    private final List<EmbeddingClient> clients;
    private final AiConfig aiConfig;

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
            return aiConfig.getEmbedding().getDefaultProvider();
        }
        EmbeddingProvider resolvedProvider = EmbeddingProvider.valueOf(provider.trim().toUpperCase());
        if (resolvedProvider == EmbeddingProvider.OPENAI && !aiConfig.getOpenai().isEmbeddingEnabled()) {
            throw new IllegalArgumentException("OpenAI embedding 호출이 현재 설정에서 비활성화되어 있습니다. provider=OLLAMA를 사용하거나 ai.openai.embedding-enabled=true로 변경하세요.");
        }
        return resolvedProvider;
    }
}
