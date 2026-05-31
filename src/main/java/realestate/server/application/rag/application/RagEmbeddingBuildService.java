package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realestate.server.application.rag.domain.EmbeddingClient;
import realestate.server.application.rag.domain.EmbeddingModelProfile;
import realestate.server.application.rag.domain.RagDocumentForEmbedding;
import realestate.server.application.rag.domain.RagDocumentRepository;
import realestate.server.application.rag.domain.RagEmbeddingBuildResult;
import realestate.server.application.rag.domain.RagEmbeddingToSave;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagEmbeddingBuildService {

    private final RagDocumentRepository ragDocumentRepository;
    private final EmbeddingClientRegistry embeddingClientRegistry;

    @Transactional
    public RagEmbeddingBuildResult buildDocumentEmbeddings(int limit, String provider, String model) {
        EmbeddingModelProfile profile = embeddingClientRegistry.resolveProfile(provider, model);
        EmbeddingClient embeddingClient = embeddingClientRegistry.resolve(profile.provider());
        List<RagDocumentForEmbedding> documents = ragDocumentRepository.findDocumentsWithoutEmbedding(profile, limit);
        if (documents.isEmpty()) {
            return new RagEmbeddingBuildResult(profile.provider().name(), profile.model(), 0, 0, 0);
        }

        List<String> inputs = documents.stream()
                .map(RagDocumentForEmbedding::content)
                .toList();

        List<List<Double>> embeddings = embeddingClient.embed(profile.model(), inputs);
        if (embeddings.size() != documents.size()) {
            throw new IllegalStateException("Embedding 응답 개수가 요청 문서 개수와 다릅니다.");
        }

        List<RagEmbeddingToSave> embeddingsToSave = java.util.stream.IntStream.range(0, documents.size())
                .mapToObj(i -> new RagEmbeddingToSave(documents.get(i).id(), embeddings.get(i)))
                .toList();

        int embeddedCount = 0;
        int failedCount = 0;
        try {
            embeddedCount = ragDocumentRepository.saveEmbeddings(profile, embeddingsToSave);
        } catch (Exception e) {
            failedCount = embeddingsToSave.size();
            log.error("RAG embedding batch 저장 실패 - provider: {}, model: {}, count: {}",
                    profile.provider(), profile.model(), embeddingsToSave.size(), e);
        }

        int skippedCount = documents.size() - embeddedCount - failedCount;
        log.info("RAG embedding build completed - provider: {}, model: {}, embedded: {}, skipped: {}, failed: {}, limit: {}",
                profile.provider(), profile.model(), embeddedCount, skippedCount, failedCount, limit);
        return new RagEmbeddingBuildResult(profile.provider().name(), profile.model(), embeddedCount, skippedCount, failedCount);
    }
}
