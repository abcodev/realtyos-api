package realtyos.server.application.common.ai;

import realtyos.server.application.common.ai.prompt.AiPromptTemplateJpaEntity;
import realtyos.server.application.common.ai.prompt.AiPromptTemplateRepository;
import realtyos.server.application.common.ai.routing.AiModelRouter;
import realtyos.server.application.common.ai.routing.AiRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 에이전트 메인 서비스.
 * <p>
 * DB에서 엔티티 타입 + AI 공급자별 활성 프롬프트를 조회하고, 해당 공급자를 통해 응답을 생성합니다.
 *
 * <pre>
 * 사용 예시:
 *   // 공급자를 직접 지정
 *   String result = aiService.ask(AiProvider.OPENAI, "FINANCE", "오늘의 주식 시장을 요약해줘");
 *
 *   // 공급자 미지정 — DB에서 entityType에 매핑된 활성 프롬프트의 공급자 자동 사용
 *   String result = aiService.ask("SPORTS", "어제 경기 결과를 알려줘");
 * </pre>
 */
@Service
@Slf4j
public class AiService {

    private final Map<AiProvider, AiClient> clientMap;
    private final AiPromptTemplateRepository promptTemplateRepository;
    private final AiModelRouter modelRouter;

    public AiService(List<AiClient> clients, AiPromptTemplateRepository promptTemplateRepository,
                     AiModelRouter modelRouter) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(AiClient::getProvider, Function.identity()));
        this.promptTemplateRepository = promptTemplateRepository;
        this.modelRouter = modelRouter;
    }

    /**
     * AI 공급자를 지정하여 질문합니다.
     *
     * @param provider    사용할 AI 공급자 (OPENAI, GEMINI)
     * @param entityType  엔티티 타입 (예: FINANCE, SPORTS)
     * @param userMessage 사용자 메시지
     * @return AI 응답 텍스트
     */
    public String ask(AiProvider provider, String entityType, String userMessage) {
        return ask(provider, entityType, userMessage, null);
    }

    public String ask(AiProvider provider, String entityType, String userMessage, String model) {
        AiPromptTemplateJpaEntity template = promptTemplateRepository
                .findByEntityTypeAndAiProviderAndIsActiveTrue(entityType, provider.name())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("활성 프롬프트가 없습니다: entityType=%s, provider=%s", entityType, provider)));

        return executeChat(provider, template, userMessage, model);
    }

    public String askRouted(String entityType, String userMessage, AiProvider requestedProvider, String requestedModel) {
        AiRoute route = modelRouter.route(entityType, userMessage, requestedProvider, requestedModel);
        try {
            return ask(route.provider(), entityType, userMessage, route.model());
        } catch (RuntimeException primaryFailure) {
            if (route.fallbackProvider() == null || route.fallbackProvider() == route.provider()) {
                throw primaryFailure;
            }
            log.warn("AI primary route failed - entityType: {}, provider: {}, model: {}, fallbackProvider: {}, fallbackModel: {}, reason: {}",
                    entityType, route.provider(), route.model(), route.fallbackProvider(), route.fallbackModel(),
                    route.reason(), primaryFailure);
            return ask(route.fallbackProvider(), entityType, userMessage, route.fallbackModel());
        }
    }

    public AiRoute route(String entityType, String userMessage, AiProvider requestedProvider, String requestedModel) {
        return modelRouter.route(entityType, userMessage, requestedProvider, requestedModel);
    }

    public void stream(AiRoute route, String entityType, String userMessage, Consumer<String> onChunk) {
        try {
            executeStream(route.provider(), entityType, userMessage, route.model(), onChunk);
        } catch (RuntimeException primaryFailure) {
            if (route.fallbackProvider() == null || route.fallbackProvider() == route.provider()) {
                throw primaryFailure;
            }
            log.warn("AI primary stream route failed - entityType: {}, provider: {}, model: {}, fallbackProvider: {}, fallbackModel: {}, reason: {}",
                    entityType, route.provider(), route.model(), route.fallbackProvider(), route.fallbackModel(),
                    route.reason(), primaryFailure);
            executeStream(route.fallbackProvider(), entityType, userMessage, route.fallbackModel(), onChunk);
        }
    }

    /**
     * AI 공급자를 미지정하여 질문합니다.
     * DB에서 entityType에 매핑된 활성 프롬프트의 ai_provider를 자동으로 사용합니다.
     *
     * @param entityType  엔티티 타입 (예: FINANCE, SPORTS)
     * @param userMessage 사용자 메시지
     * @return AI 응답 텍스트
     */
    public String ask(String entityType, String userMessage) {
        AiPromptTemplateJpaEntity template = promptTemplateRepository
                .findByEntityTypeAndIsActiveTrue(entityType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "활성 프롬프트가 없습니다: entityType=" + entityType));

        AiProvider provider = AiProvider.valueOf(template.getAiProvider());
        return executeChat(provider, template, userMessage, null);
    }

    private String executeChat(AiProvider provider, AiPromptTemplateJpaEntity template, String userMessage, String model) {
        AiClient client = clientMap.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 AI 공급자입니다: " + provider);
        }

        log.info("AI 호출 - provider: {}, entityType: {}, model: {}, version: {}",
                provider, template.getEntityType(), model != null ? model : template.getModel(), template.getVersion());

        String response = client.chat(template, userMessage, model);

        log.info("AI 응답 완료 - provider: {}, entityType: {}, 응답 길이: {}",
                provider, template.getEntityType(), response.length());

        return response;
    }

    private void executeStream(AiProvider provider, String entityType, String userMessage, String model,
                               Consumer<String> onChunk) {
        AiPromptTemplateJpaEntity template = promptTemplateRepository
                .findByEntityTypeAndAiProviderAndIsActiveTrue(entityType, provider.name())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("활성 프롬프트가 없습니다: entityType=%s, provider=%s", entityType, provider)));

        AiClient client = clientMap.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 AI 공급자입니다: " + provider);
        }

        log.info("AI 스트리밍 호출 - provider: {}, entityType: {}, model: {}, version: {}",
                provider, entityType, model != null ? model : template.getModel(), template.getVersion());

        client.streamChat(template, userMessage, model, onChunk);
    }
}
