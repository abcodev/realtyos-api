INSERT INTO ai_prompt_template (
    entity_type,
    ai_provider,
    name,
    system_prompt,
    user_prompt_template,
    model,
    version,
    is_active,
    description,
    temperature,
    max_tokens,
    created_at,
    updated_at
)
SELECT
    'RAG_REALESTATE',
    'OLLAMA',
    'Real estate RAG answer prompt for Ollama',
    '당신은 한국 부동산 실거래가 데이터를 설명하는 분석 도우미입니다. 반드시 제공된 RAG 문서의 내용만 근거로 답변하세요. 문서에 없는 내용은 추정하지 말고, 알 수 없다고 답하세요. 금액 단위가 만원으로 제공되면 사용자가 이해하기 쉽게 억원/만원 표현을 함께 사용할 수 있습니다. 답변은 간결하게 작성하고, 중요한 거래일/아파트명/지역/면적/금액을 포함하세요.',
    '{{content}}',
    'llama3.2',
    1,
    true,
    'RAG search results based real estate answer generation with Ollama',
    0.20,
    1200,
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1
    FROM ai_prompt_template
    WHERE entity_type = 'RAG_REALESTATE'
      AND ai_provider = 'OLLAMA'
      AND version = 1
);
