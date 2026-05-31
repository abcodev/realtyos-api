package realestate.server.application.common.ai;

import realestate.server.application.common.ai.prompt.AiPromptTemplateJpaEntity;

/**
 * AI 클라이언트 공통 인터페이스.
 * <p>
 * Strategy 패턴으로 OpenAI, Gemini 등 구현체를 동적으로 선택합니다.
 */
public interface AiClient {

    /**
     * AI에게 메시지를 전송하고 응답을 받습니다.
     *
     * @param template    DB에서 조회한 프롬프트 템플릿 (모델, 온도, 최대 토큰 등 포함)
     * @param userMessage 사용자 메시지 (실제 질문/요청)
     * @return AI 응답 텍스트
     */
    String chat(AiPromptTemplateJpaEntity template, String userMessage);

    default String chat(AiPromptTemplateJpaEntity template, String userMessage, String model) {
        return chat(template, userMessage);
    }

    /**
     * 이 클라이언트의 AI 공급자를 반환합니다.
     *
     * @return AI 공급자
     */
    AiProvider getProvider();
}
