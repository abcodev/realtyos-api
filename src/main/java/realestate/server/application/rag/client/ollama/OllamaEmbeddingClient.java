package realestate.server.application.rag.client.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import realestate.server.application.common.ai.config.AiConfig;
import realestate.server.application.rag.domain.EmbeddingClient;
import realestate.server.application.rag.domain.EmbeddingProvider;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final String EMBED_PATH = "/api/embed";

    private final WebClient webClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    @Override
    public EmbeddingProvider provider() {
        return EmbeddingProvider.OLLAMA;
    }

    @Override
    public String defaultModel() {
        return aiConfig.getOllama().getEmbeddingModel();
    }

    @Override
    public List<List<Double>> embed(String model, List<String> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }

        List<List<Double>> embeddings = new ArrayList<>();
        int batchSize = resolveBatchSize();
        for (int from = 0; from < inputs.size(); from += batchSize) {
            int to = Math.min(from + batchSize, inputs.size());
            embeddings.addAll(embedBatch(model, inputs.subList(from, to)));
        }
        return embeddings;
    }

    private int resolveBatchSize() {
        return Math.max(1, aiConfig.getOllama().getEmbeddingBatchSize());
    }

    private List<List<Double>> embedBatch(String model, List<String> inputs) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            ArrayNode inputArray = requestBody.putArray("input");
            inputs.forEach(inputArray::add);

            String responseBody = webClient.post()
                    .uri(aiConfig.getOllama().getBaseUrl() + EMBED_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new IllegalStateException(
                                    "Ollama embedding API error - status: %s, body: %s"
                                            .formatted(response.statusCode(), body)))))
                    .bodyToMono(String.class)
                    .block();

            return extractEmbeddings(responseBody);
        } catch (Exception e) {
            log.error("Ollama embedding API 호출 실패", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private List<List<Double>> extractEmbeddings(String responseBody) throws Exception {
        JsonNode embeddings = objectMapper.readTree(responseBody).path("embeddings");
        List<List<Double>> result = new ArrayList<>();
        for (JsonNode item : embeddings) {
            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : item) {
                embedding.add(value.asDouble());
            }
            result.add(embedding);
        }
        return result;
    }
}
