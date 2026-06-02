package realtyos.server.application.rag.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import realtyos.server.application.rag.domain.RagEmbeddingBuildResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REAL_OPENAI_EMBEDDING_TEST", matches = "true")
class RagEmbeddingBuildIntegrationTest {

    private static final long TEST_DOCUMENT_ID = -900_000_001L;
    private static final String TEST_SOURCE_TYPE = "TEST_OPENAI_EMBEDDING";
    private static final String TEST_MODEL = "text-embedding-3-small";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RagEmbeddingBuildService embeddingBuildService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM rag_embedding WHERE document_id = ?", TEST_DOCUMENT_ID);
        jdbcTemplate.update("DELETE FROM rag_document WHERE id = ?", TEST_DOCUMENT_ID);
    }

    @Test
    void buildsAndStoresOpenAiEmbeddingForMissingDocument() {
        tearDown();
        jdbcTemplate.update("""
                        INSERT INTO rag_document (
                            id,
                            title,
                            content,
                            apartment_name,
                            region,
                            source_type,
                            source_id,
                            content_hash
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, md5(?))
                        """,
                TEST_DOCUMENT_ID,
                "테스트 강남구 대치동 아파트 실거래",
                "문서유형: 아파트 실거래가\n지역키워드: 11680 대치동\n아파트명: 테스트아파트\n거래금액: 100000만원",
                "테스트아파트",
                "11680 대치동",
                TEST_SOURCE_TYPE,
                TEST_DOCUMENT_ID,
                "문서유형: 아파트 실거래가\n지역키워드: 11680 대치동\n아파트명: 테스트아파트\n거래금액: 100000만원"
        );

        RagEmbeddingBuildResult result = embeddingBuildService.buildDocumentEmbeddings(
                1,
                "OPENAI",
                TEST_MODEL
        );

        Integer savedCount = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM rag_embedding
                        WHERE document_id = ?
                        AND provider = 'OPENAI'
                        AND model = ?
                        AND dimension = 1536
                        """,
                Integer.class,
                TEST_DOCUMENT_ID,
                TEST_MODEL
        );

        assertThat(result.embeddedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(savedCount).isEqualTo(1);
    }
}
