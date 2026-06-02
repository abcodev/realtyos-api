package realtyos.server.application.common.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import realtyos.server.application.common.ai.AiProvider;

/**
 * AI API 키 설정.
 * application.yml의 ai 설정 값을 바인딩합니다.
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AiConfig {

    private OpenAi openai = new OpenAi();
    private Gemini gemini = new Gemini();
    private Ollama ollama = new Ollama();
    private Embedding embedding = new Embedding();
    private Router router = new Router();

    @Getter
    @Setter
    public static class OpenAi {
        private String key;
        private String embeddingModel = "text-embedding-3-small";
        private boolean embeddingEnabled = true;
        private int embeddingBatchSize = 50;
        private int embeddingMaxInputChars = 1200;
    }

    @Getter
    @Setter
    public static class Gemini {
        private String key;
    }

    @Getter
    @Setter
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String chatModel = "qwen3:8b";
        private String embeddingModel = "nomic-embed-text";
        private int embeddingBatchSize = 10;
        private boolean thinkEnabled = false;
        private int chatNumCtx = 4096;
        private int chatNumPredict = 1200;
    }

    @Getter
    @Setter
    public static class Embedding {
        private realtyos.server.application.rag.domain.EmbeddingProvider defaultProvider =
                realtyos.server.application.rag.domain.EmbeddingProvider.OPENAI;
    }

    @Getter
    @Setter
    public static class Router {
        private boolean enabled = true;
        private boolean localFirst = true;
        private int localMaxInputChars = 6000;
        private AiProvider defaultProvider = AiProvider.OPENAI;
        private String defaultModel = "gpt-4o-mini";
        private AiProvider localProvider = AiProvider.OLLAMA;
        private String localModel = "qwen3:8b";
        private AiProvider highQualityProvider = AiProvider.OPENAI;
        private String highQualityModel = "gpt-4o-mini";
        private AiProvider fallbackProvider = AiProvider.OPENAI;
        private String fallbackModel = "gpt-4o-mini";
    }
}
