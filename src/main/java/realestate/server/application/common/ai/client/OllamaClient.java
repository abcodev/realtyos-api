package realestate.server.application.common.ai.client;

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
import realestate.server.application.common.ai.AiClient;
import realestate.server.application.common.ai.AiProvider;
import realestate.server.application.common.ai.config.AiConfig;
import realestate.server.application.common.ai.prompt.AiPromptTemplateJpaEntity;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaClient implements AiClient {

    private static final String CHAT_PATH = "/api/chat";

    private final WebClient webClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    @Override
    public String chat(AiPromptTemplateJpaEntity template, String userMessage) {
        return chat(template, userMessage, null);
    }

    @Override
    public String chat(AiPromptTemplateJpaEntity template, String userMessage, String model) {
        try {
            ObjectNode requestBody = buildRequestBody(template, applyUserPromptTemplate(template, userMessage), model);

            String responseBody = webClient.post()
                    .uri(aiConfig.getOllama().getBaseUrl() + CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new IllegalStateException(
                                    "Ollama chat API error - status: %s, body: %s"
                                            .formatted(response.statusCode(), body)))))
                    .bodyToMono(String.class)
                    .block();

            return extractContent(responseBody);
        } catch (Exception e) {
            log.error("Ollama API 호출 실패", e);
            throw new RuntimeException("Ollama API 호출 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.OLLAMA;
    }

    private String applyUserPromptTemplate(AiPromptTemplateJpaEntity template, String userMessage) {
        if (template.getUserPromptTemplate() != null && !template.getUserPromptTemplate().isBlank()) {
            return template.getUserPromptTemplate().replace("{{content}}", userMessage);
        }
        return userMessage;
    }

    private ObjectNode buildRequestBody(AiPromptTemplateJpaEntity template, String userMessage, String modelOverride) {
        ObjectNode body = objectMapper.createObjectNode();
        String model = modelOverride != null && !modelOverride.isBlank()
                ? modelOverride
                : template.getModel() != null ? template.getModel() : aiConfig.getOllama().getChatModel();
        body.put("model", model);
        body.put("stream", false);
        if (template.getTemperature() != null) {
            ObjectNode options = body.putObject("options");
            options.put("temperature", template.getTemperature().doubleValue());
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", template.getSystemPrompt());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        return body;
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("message").path("content").asText();
    }
}
