package realtyos.server.application.rag.application;

import org.junit.jupiter.api.Test;
import realtyos.server.application.common.ai.config.AiConfig;
import realtyos.server.application.rag.domain.EmbeddingClient;
import realtyos.server.application.rag.domain.EmbeddingModelProfile;
import realtyos.server.application.rag.domain.EmbeddingProvider;
import realtyos.server.application.rag.domain.RagDocumentForEmbedding;
import realtyos.server.application.rag.domain.RagDocumentRepository;
import realtyos.server.application.rag.domain.RagEmbeddingBuildResult;
import realtyos.server.application.rag.domain.RagEmbeddingToSave;
import realtyos.server.application.rag.domain.RagIndexStats;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagEmbeddingBuildServiceTest {

    @Test
    void buildsEmbeddingsOnlyForDocumentsWithoutEmbedding() {
        FakeRagDocumentRepository repository = new FakeRagDocumentRepository(List.of(
                new RagDocumentForEmbedding(11L, "강남구 대치동 래미안대치팰리스 실거래"),
                new RagDocumentForEmbedding(22L, "강남구 개포동 개포자이프레지던스 실거래")
        ));
        FakeEmbeddingClient embeddingClient = new FakeEmbeddingClient();
        RagEmbeddingBuildService service = new RagEmbeddingBuildService(
                repository,
                new EmbeddingClientRegistry(List.of(embeddingClient), new AiConfig())
        );

        RagEmbeddingBuildResult result = service.buildDocumentEmbeddings(
                2,
                "OPENAI",
                "text-embedding-3-small"
        );

        assertThat(result.provider()).isEqualTo("OPENAI");
        assertThat(result.model()).isEqualTo("text-embedding-3-small");
        assertThat(result.embeddedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();

        assertThat(repository.requestedProfile)
                .isEqualTo(new EmbeddingModelProfile(EmbeddingProvider.OPENAI, "text-embedding-3-small"));
        assertThat(repository.requestedLimit).isEqualTo(2);
        assertThat(embeddingClient.requestedInputs).containsExactly(
                "강남구 대치동 래미안대치팰리스 실거래",
                "강남구 개포동 개포자이프레지던스 실거래"
        );
        assertThat(repository.savedEmbeddings)
                .extracting(RagEmbeddingToSave::documentId)
                .containsExactly(11L, 22L);
    }

    @Test
    void skipsEmbeddingClientWhenMissingDocumentsDoNotExist() {
        FakeRagDocumentRepository repository = new FakeRagDocumentRepository(List.of());
        FakeEmbeddingClient embeddingClient = new FakeEmbeddingClient();
        RagEmbeddingBuildService service = new RagEmbeddingBuildService(
                repository,
                new EmbeddingClientRegistry(List.of(embeddingClient), new AiConfig())
        );

        RagEmbeddingBuildResult result = service.buildDocumentEmbeddings(
                100,
                "OPENAI",
                "text-embedding-3-small"
        );

        assertThat(result.embeddedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(embeddingClient.requestedInputs).isEmpty();
        assertThat(repository.savedEmbeddings).isEmpty();
    }

    private static class FakeRagDocumentRepository implements RagDocumentRepository {

        private final List<RagDocumentForEmbedding> documentsWithoutEmbedding;
        private EmbeddingModelProfile requestedProfile;
        private int requestedLimit;
        private List<RagEmbeddingToSave> savedEmbeddings = List.of();

        private FakeRagDocumentRepository(List<RagDocumentForEmbedding> documentsWithoutEmbedding) {
            this.documentsWithoutEmbedding = documentsWithoutEmbedding;
        }

        @Override
        public int buildDealDocuments(int limit) {
            return 0;
        }

        @Override
        public List<RagDocumentForEmbedding> findDocumentsWithoutEmbedding(EmbeddingModelProfile profile, int limit) {
            this.requestedProfile = profile;
            this.requestedLimit = limit;
            return documentsWithoutEmbedding;
        }

        @Override
        public int saveEmbeddings(EmbeddingModelProfile profile, List<RagEmbeddingToSave> embeddings) {
            this.savedEmbeddings = embeddings;
            return embeddings.size();
        }

        @Override
        public List<RagSearchResult> searchByEmbedding(
                EmbeddingModelProfile profile,
                List<Double> embedding,
                int topK,
                RagSearchCondition condition
        ) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> searchDealsByKeyword(int topK, RagSearchCondition condition) {
            return List.of();
        }

        @Override
        public RagIndexStats getIndexStats(EmbeddingModelProfile profile) {
            return null;
        }
    }

    private static class FakeEmbeddingClient implements EmbeddingClient {

        private final List<String> requestedInputs = new ArrayList<>();

        @Override
        public EmbeddingProvider provider() {
            return EmbeddingProvider.OPENAI;
        }

        @Override
        public String defaultModel() {
            return "text-embedding-3-small";
        }

        @Override
        public List<List<Double>> embed(String model, List<String> inputs) {
            requestedInputs.addAll(inputs);
            return inputs.stream()
                    .map(input -> List.of(0.1, 0.2, 0.3))
                    .toList();
        }
    }
}
