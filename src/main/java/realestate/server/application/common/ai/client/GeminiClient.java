package realestate.server.application.common.ai.client;

import realestate.server.application.common.ai.AiClient;
import realestate.server.application.common.ai.AiProvider;
import realestate.server.application.common.ai.config.AiConfig;
import realestate.server.application.common.ai.prompt.AiPromptTemplateJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Google Gemini API 클라이언트.
 * <p>
 * DB 프롬프트 템플릿의 model, temperature, maxTokens 값을 사용합니다.
 * 값이 없으면 기본값 (gemini-2.0-flash, 0.7, 1024)을 사용합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient implements AiClient {

    private static final String BASE_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final WebClient webClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    @Override
    public String chat(AiPromptTemplateJpaEntity template, String userMessage) {
        return chat(template, userMessage, null);
    }

    @Override
    public String chat(AiPromptTemplateJpaEntity template, String userMessage, String modelOverride) {
        try {
            String actualUserMessage = applyUserPromptTemplate(template, userMessage);
            ObjectNode requestBody = buildRequestBody(template, actualUserMessage);

            String model = modelOverride != null && !modelOverride.isBlank()
                    ? modelOverride
                    : template.getModel() != null ? template.getModel() : DEFAULT_MODEL;
            String url = String.format(BASE_URL_TEMPLATE, model) + "?key=" + aiConfig.getGemini().getKey();

            log.info("[Gemini] 시스템 프롬프트: {}",
                    requestBody.path("system_instruction").path("parts").get(0).path("text").asText());
            log.info("[Gemini] 사용자 메시지: {}", actualUserMessage);

            String responseBody = webClient.post()
                    .uri(URI.create(url))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractContent(responseBody);
        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            throw new RuntimeException("Gemini API 호출 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GEMINI;
    }

    private String applyUserPromptTemplate(AiPromptTemplateJpaEntity template, String userMessage) {
        if (template.getUserPromptTemplate() != null && !template.getUserPromptTemplate().isBlank()) {
            return template.getUserPromptTemplate().replace("{{content}}", userMessage);
        }
        return userMessage;
    }

    private ObjectNode buildRequestBody(AiPromptTemplateJpaEntity template, String userMessage) {
        ObjectNode body = objectMapper.createObjectNode();

        // system instruction (${sysdate} → 실제 호출 시간 바인딩)
        String systemPrompt = template.getSystemPrompt()
                .replace("${sysdate}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        ObjectNode systemInstruction = body.putObject("system_instruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        ObjectNode systemPart = systemParts.addObject();
        systemPart.put("text", systemPrompt);

        // contents (user message)
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        ObjectNode part = parts.addObject();
        part.put("text", userMessage);

        // generation config (temperature, maxOutputTokens)
        ObjectNode generationConfig = body.putObject("generationConfig");
        if (template.getTemperature() != null) {
            generationConfig.put("temperature", template.getTemperature().doubleValue());
        }
        if (template.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", template.getMaxTokens());
        }

        return body;
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패: {}", responseBody, e);
            throw new RuntimeException("Gemini 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }
}
