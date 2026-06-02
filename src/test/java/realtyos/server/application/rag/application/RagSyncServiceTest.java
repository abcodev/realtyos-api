package realtyos.server.application.rag.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import realtyos.server.application.rag.domain.RagSyncResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("loc")
class RagSyncServiceTest {

    @Autowired
    private RagSyncService ragSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void RAG_문서와_선택한_모델_임베딩을_동기화하면_임베딩_개수가_증가하거나_유지된다() {
        String embeddingProvider = embeddingProvider();
        String embeddingModel = embeddingModel();
        int documentLimit = intProperty("rag.test.document-limit", 100);
        int embeddingLimit = intProperty("rag.test.embedding-limit", 10);
        boolean repeatUntilComplete = booleanProperty("rag.test.repeat-until-complete", false);
        int maxBatches = intProperty("rag.test.max-batches", 1);

        long initialDocumentCount = countDocuments();
        long initialEmbeddingCount = countEmbeddings(embeddingProvider, embeddingModel);
        long initialMissingEmbeddingCount = countMissingEmbeddings(embeddingProvider, embeddingModel);

        RagSyncResult result = null;
        int executedBatches = 0;
        do {
            result = ragSyncService.syncDealDocumentsAndEmbeddings(
                    documentLimit,
                    embeddingLimit,
                    embeddingProvider,
                    embeddingModel
            );
            executedBatches++;
            System.out.println("실행 배치: " + executedBatches + ", 생성된 임베딩 수: " + result.embeddedCount());
        } while (repeatUntilComplete
                && executedBatches < maxBatches
                && result.embeddedCount() > 0
                && countMissingEmbeddings(embeddingProvider, embeddingModel) > 0);

        long newDocumentCount = countDocuments();
        long newEmbeddingCount = countEmbeddings(embeddingProvider, embeddingModel);
        long newMissingEmbeddingCount = countMissingEmbeddings(embeddingProvider, embeddingModel);

        System.out.println("임베딩 provider: " + embeddingProvider);
        System.out.println("임베딩 model: " + embeddingModel);
        System.out.println("초기 RAG 문서 수: " + initialDocumentCount);
        System.out.println("동기화 후 RAG 문서 수: " + newDocumentCount);
        System.out.println("초기 임베딩 수: " + initialEmbeddingCount);
        System.out.println("동기화 후 임베딩 수: " + newEmbeddingCount);
        System.out.println("초기 누락 임베딩 수: " + initialMissingEmbeddingCount);
        System.out.println("동기화 후 누락 임베딩 수: " + newMissingEmbeddingCount);
        System.out.println("마지막 배치 생성 임베딩 수: " + result.embeddedCount());
        System.out.println("실행된 배치 수: " + executedBatches);

        assertThat(newDocumentCount).isGreaterThanOrEqualTo(initialDocumentCount);
        assertThat(result.embeddingProvider()).isEqualTo(embeddingProvider);
        assertThat(result.embeddingModel()).isEqualTo(embeddingModel);
        assertThat(newEmbeddingCount).isGreaterThanOrEqualTo(initialEmbeddingCount);
        assertThat(result.failedCount()).isZero();
        if (initialMissingEmbeddingCount > 0) {
            assertThat(result.embeddedCount()).isGreaterThan(0);
            assertThat(newEmbeddingCount).isGreaterThan(initialEmbeddingCount);
            assertThat(newMissingEmbeddingCount).isLessThan(initialMissingEmbeddingCount);
        }
    }

    private long countDocuments() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM rag_document", Long.class);
        return count == null ? 0 : count;
    }

    private long countEmbeddings(String embeddingProvider, String embeddingModel) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM rag_embedding
                WHERE provider = ?
                AND model = ?
                """, Long.class, embeddingProvider, embeddingModel);
        return count == null ? 0 : count;
    }

    private long countMissingEmbeddings(String embeddingProvider, String embeddingModel) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM rag_document rd
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM rag_embedding re
                    WHERE re.document_id = rd.id
                    AND re.provider = ?
                    AND re.model = ?
                )
                """, Long.class, embeddingProvider, embeddingModel);
        return count == null ? 0 : count;
    }

    private String embeddingProvider() {
        return System.getProperty("rag.test.embedding-provider", "OPENAI");
    }

    private String embeddingModel() {
        return System.getProperty("rag.test.embedding-model", "text-embedding-3-small");
    }

    private int intProperty(String key, int defaultValue) {
        return Integer.parseInt(System.getProperty(key, String.valueOf(defaultValue)));
    }

    private boolean booleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(System.getProperty(key, String.valueOf(defaultValue)));
    }
}
