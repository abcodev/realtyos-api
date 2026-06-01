package realtyos.server.application.rag.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagQueryRewritePolicyTest {

    private final RagQueryRewritePolicy policy = new RagQueryRewritePolicy();

    @Test
    void rewritesNaturalLanguageQueryToSearchCondition() {
        RagQueryRewriteResult result = policy.rewrite("강남 최근 10억 이상 84제곱 아파트 알려줘", null);

        assertThat(result.rewrittenQuery()).contains("실거래가");
        assertThat(result.condition().region()).isEqualTo("강남");
        assertThat(result.condition().minPrice()).isEqualTo(100000L);
        assertThat(result.condition().maxPrice()).isNull();
        assertThat(result.condition().minArea()).isEqualTo(79.0);
        assertThat(result.condition().maxArea()).isEqualTo(89.0);
        assertThat(result.condition().recentFirst()).isTrue();
    }

    @Test
    void explicitConditionHasPriorityOverInferredCondition() {
        RagSearchCondition explicit = new RagSearchCondition(
                "서초",
                null,
                null,
                null,
                null,
                null,
                120000L,
                null,
                null,
                null,
                null
        );

        RagQueryRewriteResult result = policy.rewrite("강남 최근 10억 이상", explicit);

        assertThat(result.condition().region()).isEqualTo("서초");
        assertThat(result.condition().minPrice()).isEqualTo(120000L);
        assertThat(result.condition().recentFirst()).isTrue();
    }

    @Test
    void convertsPyeongRangeToSquareMeterCondition() {
        RagQueryRewriteResult result = policy.rewrite("강남 30평대 아파트 시세", null);

        assertThat(result.condition().region()).isEqualTo("강남");
        assertThat(result.condition().minArea()).isEqualTo(99.17);
        assertThat(result.condition().maxArea()).isEqualTo(132.23);
    }
}
