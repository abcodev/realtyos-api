package realestate.server.application.common.ai;

/**
 * AI 공급자를 구분하는 Enum.
 */
public enum AiProvider {

    /** OpenAI (GPT 계열) */
    OPENAI,

    /** Google Gemini */
    GEMINI,

    /** Ollama local models */
    OLLAMA
}
