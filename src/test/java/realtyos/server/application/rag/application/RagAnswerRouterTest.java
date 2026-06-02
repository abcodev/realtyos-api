package realtyos.server.application.rag.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagAnswerRouterTest {

    private final RagAnswerRouter router = new RagAnswerRouter();

    @Test
    void routesComparisonQuestionToComparison() {
        RagAnswerRoute route = router.route("대치동이랑 역삼동 시세 비교해줘");

        assertThat(route.type()).isEqualTo(RagAnswerRouteType.COMPARISON);
        assertThat(route.usesDecisionEngine()).isTrue();
    }

    @Test
    void routesRecommendationQuestionToRecommendation() {
        RagAnswerRoute route = router.route("마포에서 갈아타기 후보를 추천해줘");

        assertThat(route.type()).isEqualTo(RagAnswerRouteType.RECOMMENDATION);
        assertThat(route.usesDecisionEngine()).isTrue();
    }

    @Test
    void routesMarketPriceQuestionToMarketPrice() {
        RagAnswerRoute route = router.route("개포동 시세 어때");

        assertThat(route.type()).isEqualTo(RagAnswerRouteType.MARKET_PRICE);
        assertThat(route.usesDecisionEngine()).isTrue();
    }

    @Test
    void routesEvidenceLookupQuestionToSearch() {
        RagAnswerRoute route = router.route("래미안대치팰리스 최근 거래 알려줘");

        assertThat(route.type()).isEqualTo(RagAnswerRouteType.SEARCH);
        assertThat(route.usesDecisionEngine()).isFalse();
    }
}
