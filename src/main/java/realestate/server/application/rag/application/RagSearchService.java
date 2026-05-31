package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import realestate.server.application.rag.domain.EmbeddingClient;
import realestate.server.application.rag.domain.EmbeddingModelProfile;
import realestate.server.application.rag.domain.RagDocumentRepository;
import realestate.server.application.rag.domain.RagQueryRewritePolicy;
import realestate.server.application.rag.domain.RagQueryRewriteResult;
import realestate.server.application.rag.domain.RagSearchCondition;
import realestate.server.application.rag.domain.RagSearchResult;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final RagDocumentRepository ragDocumentRepository;
    private final EmbeddingClientRegistry embeddingClientRegistry;
    private final RagQueryRewritePolicy queryRewritePolicy = new RagQueryRewritePolicy();

    public List<RagSearchResult> search(
            String query,
            Integer topK,
            String provider,
            String model,
            RagSearchCondition condition
    ) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }

        int normalizedTopK = normalizeTopK(topK);
        RagQueryRewriteResult rewriteResult = queryRewritePolicy.rewrite(query, condition);
        EmbeddingModelProfile profile = embeddingClientRegistry.resolveProfile(provider, model);
        EmbeddingClient embeddingClient = embeddingClientRegistry.resolve(profile.provider());
        List<Double> queryEmbedding = embeddingClient.embed(profile.model(), List.of(rewriteResult.rewrittenQuery())).getFirst();
        List<RagSearchResult> results = ragDocumentRepository.searchByEmbedding(
                profile,
                queryEmbedding,
                normalizedTopK,
                rewriteResult.condition()
        );

        log.info("RAG search completed - provider: {}, model: {}, query: {}, rewrittenQuery: {}, topK: {}, condition: {}, resultCount: {}",
                profile.provider(), profile.model(), query, rewriteResult.rewrittenQuery(),
                normalizedTopK, rewriteResult.condition(), results.size());
        return results;
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}
