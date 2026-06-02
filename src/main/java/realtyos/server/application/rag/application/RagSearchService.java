package realtyos.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import realtyos.server.application.rag.domain.EmbeddingClient;
import realtyos.server.application.rag.domain.EmbeddingModelProfile;
import realtyos.server.application.rag.domain.RagDocumentRepository;
import realtyos.server.application.rag.domain.RagQueryRewritePolicy;
import realtyos.server.application.rag.domain.RagQueryRewriteResult;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<String> comparisonRegions = queryRewritePolicy.inferComparisonRegions(query);
        if (!comparisonRegions.isEmpty()) {
            results = mergeComparisonRegionResults(results, comparisonRegions, normalizedTopK, rewriteResult.condition());
        }
        if (results.isEmpty()) {
            results = ragDocumentRepository.searchDealsByKeyword(normalizedTopK, rewriteResult.condition());
            log.info("RAG embedding search returned empty. keyword fallback completed - query: {}, condition: {}, resultCount: {}",
                    query, rewriteResult.condition(), results.size());
        }

        log.info("RAG search completed - provider: {}, model: {}, query: {}, rewrittenQuery: {}, topK: {}, condition: {}, resultCount: {}",
                profile.provider(), profile.model(), query, rewriteResult.rewrittenQuery(),
                normalizedTopK, rewriteResult.condition(), results.size());
        return results;
    }

    private List<RagSearchResult> mergeComparisonRegionResults(
            List<RagSearchResult> baseResults,
            List<String> comparisonRegions,
            int topK,
            RagSearchCondition condition
    ) {
        Map<String, RagSearchResult> merged = new LinkedHashMap<>();

        int perRegionTopK = Math.max(2, Math.min(4, topK));
        for (String region : comparisonRegions) {
            RagSearchCondition regionCondition = withRegion(condition, region);
            List<RagSearchResult> regionResults = ragDocumentRepository.searchDealsByKeyword(perRegionTopK, regionCondition);
            for (RagSearchResult result : regionResults) {
                merged.putIfAbsent(resultKey(result), result);
            }
        }

        for (RagSearchResult result : baseResults) {
            if (matchesAnyComparisonRegion(result, comparisonRegions)) {
                merged.putIfAbsent(resultKey(result), result);
            }
        }

        return new ArrayList<>(merged.values()).stream()
                .limit(Math.min(MAX_TOP_K, comparisonRegions.size() * perRegionTopK))
                .toList();
    }

    private RagSearchCondition withRegion(RagSearchCondition condition, String region) {
        if (condition == null) {
            return new RagSearchCondition(region, null, null, null, null, null, null, null, null, null, null);
        }
        return new RagSearchCondition(
                region,
                condition.apartmentName(),
                condition.fromYear(),
                condition.fromMonth(),
                condition.toYear(),
                condition.toMonth(),
                condition.minPrice(),
                condition.maxPrice(),
                condition.minArea(),
                condition.maxArea(),
                condition.recentFirst()
        );
    }

    private String resultKey(RagSearchResult result) {
        if (result.sourceType() != null && result.sourceId() != null) {
            return result.sourceType() + ":" + result.sourceId();
        }
        return String.valueOf(result.documentId());
    }

    private boolean matchesAnyComparisonRegion(RagSearchResult result, List<String> comparisonRegions) {
        String region = result.region() == null ? "" : result.region();
        String apartmentName = result.apartmentName() == null ? "" : result.apartmentName();
        return comparisonRegions.stream()
                .anyMatch(comparisonRegion -> region.contains(comparisonRegion) || apartmentName.contains(comparisonRegion));
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}
