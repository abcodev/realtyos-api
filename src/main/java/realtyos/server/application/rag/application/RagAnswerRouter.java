package realtyos.server.application.rag.application;

import org.springframework.stereotype.Component;

@Component
public class RagAnswerRouter {

    public RagAnswerRoute route(String query) {
        if (query == null || query.isBlank()) {
            return new RagAnswerRoute(RagAnswerRouteType.SEARCH, "empty_query");
        }
        if (containsAny(query, "비교", "차이", "대비", " vs ", "VS", "와", "과", "랑", "하고", "이랑", "중 어디", "어디가")) {
            return new RagAnswerRoute(RagAnswerRouteType.COMPARISON, "comparison_intent");
        }
        if (containsAny(query, "추천", "후보", "의사결정", "살만", "매수", "투자", "실거주", "골라", "괜찮", "나아", "갈아타기")) {
            return new RagAnswerRoute(RagAnswerRouteType.RECOMMENDATION, "recommendation_intent");
        }
        if (containsAny(query, "시세", "흐름", "어때", "어떤가", "최근 거래 흐름", "평균가", "중위가", "평당가")) {
            return new RagAnswerRoute(RagAnswerRouteType.MARKET_PRICE, "market_price_intent");
        }
        return new RagAnswerRoute(RagAnswerRouteType.SEARCH, "rag_search_intent");
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
