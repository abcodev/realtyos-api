package realtyos.server.application.rag.application;

import org.junit.jupiter.api.Test;
import realtyos.server.application.common.ai.config.AiConfig;
import realtyos.server.application.rag.domain.EmbeddingClient;
import realtyos.server.application.rag.domain.EmbeddingModelProfile;
import realtyos.server.application.rag.domain.EmbeddingProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingClientRegistryTest {

    @Test
    void defaultEmbeddingProviderUsesConfiguredProvider() {
        EmbeddingClientRegistry registry = new EmbeddingClientRegistry(List.of(
                new StubEmbeddingClient(EmbeddingProvider.OPENAI, "text-embedding-3-small"),
                new StubEmbeddingClient(EmbeddingProvider.OLLAMA, "nomic-embed-text")
        ), new AiConfig());

        EmbeddingModelProfile profile = registry.resolveProfile(null, null);

        assertThat(profile.provider()).isEqualTo(EmbeddingProvider.OPENAI);
        assertThat(profile.model()).isEqualTo("text-embedding-3-small");
    }

    private record StubEmbeddingClient(EmbeddingProvider provider, String defaultModel) implements EmbeddingClient {

        @Override
        public List<List<Double>> embed(String model, List<String> inputs) {
            return List.of();
        }
    }
}
