package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final JdbcTemplate jdbcTemplate;
    private final OpenAiEmbeddingClient embeddingClient;

    public List<RagSearchResult> search(String query, Integer topK) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }

        int normalizedTopK = normalizeTopK(topK);
        List<Double> queryEmbedding = embeddingClient.embed(List.of(query)).getFirst();
        String queryVector = RagVectorLiteralFormatter.toVectorLiteral(queryEmbedding);

        List<RagSearchResult> results = jdbcTemplate.query("""
                        SELECT
                            rd.id AS document_id,
                            rd.title,
                            rd.content,
                            rd.apartment_name,
                            rd.region,
                            rd.source_type,
                            rd.source_id,
                            (re.embedding <=> ?::vector) AS distance
                        FROM rag_embedding re
                        JOIN rag_document rd ON rd.id = re.document_id
                        ORDER BY re.embedding <=> ?::vector
                        LIMIT ?
                        """,
                (rs, rowNum) -> {
                    double distance = rs.getDouble("distance");
                    return new RagSearchResult(
                            rs.getLong("document_id"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getString("apartment_name"),
                            rs.getString("region"),
                            rs.getString("source_type"),
                            rs.getLong("source_id"),
                            distance,
                            1.0 - distance
                    );
                },
                queryVector,
                queryVector,
                normalizedTopK);

        log.info("RAG search completed - query: {}, topK: {}, resultCount: {}", query, normalizedTopK, results.size());
        return results;
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}
