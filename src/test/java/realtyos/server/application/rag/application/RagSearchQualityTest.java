package realtyos.server.application.rag.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("loc")
@EnabledIfSystemProperty(named = "rag.quality.enabled", matches = "true")
class RagSearchQualityTest {

    @Autowired
    private RagSearchService searchService;

    @Test
    void 강남_30평대_검색은_강남구_30평대_전용면적만_반환한다() {
        List<RagSearchResult> results = searchService.search(
                "강남 30평대 아파트 시세",
                5,
                "OLLAMA",
                "nomic-embed-text",
                null
        );

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> {
            assertThat(result.embeddingProvider()).isEqualTo("OLLAMA");
            assertThat(result.embeddingModel()).isEqualTo("nomic-embed-text");
            assertThat(result.region()).contains("11680");
            assertThat(Double.parseDouble(result.exclusiveArea())).isBetween(99.17, 132.23);
        });
    }

    @Test
    void 대치동_래미안대치팰리스_검색은_단지명과_동을_만족한다() {
        List<RagSearchResult> results = searchService.search(
                "대치동 래미안대치팰리스 최근 실거래",
                5,
                "OLLAMA",
                "nomic-embed-text",
                new RagSearchCondition(
                        "대치동",
                        "래미안대치팰리스",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
                )
        );

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> {
            assertThat(result.region()).contains("대치동");
            assertThat(result.apartmentName()).contains("래미안대치팰리스");
        });
    }

    @Test
    void 강남_10억_이하_검색은_가격조건을_만족한다() {
        List<RagSearchResult> results = searchService.search(
                "강남 10억 이하 아파트",
                5,
                "OLLAMA",
                "nomic-embed-text",
                null
        );

        assertThat(results).allSatisfy(result -> {
            assertThat(result.region()).contains("11680");
            assertThat(result.dealAmount()).isLessThanOrEqualTo(100_000L);
        });
    }
}
