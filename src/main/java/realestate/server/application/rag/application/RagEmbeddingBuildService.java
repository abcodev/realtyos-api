package realestate.server.application.rag.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagEmbeddingBuildService {

    private final JdbcTemplate jdbcTemplate;
    private final OpenAiEmbeddingClient embeddingClient;

    @Transactional
    public RagEmbeddingBuildResult buildDocumentEmbeddings(int limit) {
        List<RagDocumentForEmbedding> documents = findDocumentsWithoutEmbedding(limit);
        if (documents.isEmpty()) {
            return new RagEmbeddingBuildResult(0, 0, 0);
        }

        List<String> inputs = documents.stream()
                .map(RagDocumentForEmbedding::content)
                .toList();

        List<List<Double>> embeddings = embeddingClient.embed(inputs);
        if (embeddings.size() != documents.size()) {
            throw new IllegalStateException("Embedding 응답 개수가 요청 문서 개수와 다릅니다.");
        }

        int embeddedCount = 0;
        int failedCount = 0;
        for (int i = 0; i < documents.size(); i++) {
            try {
                embeddedCount += insertEmbedding(documents.get(i).id(), embeddings.get(i));
            } catch (Exception e) {
                failedCount++;
                log.error("RAG embedding 저장 실패 - documentId: {}", documents.get(i).id(), e);
            }
        }

        int skippedCount = documents.size() - embeddedCount - failedCount;
        log.info("RAG embedding build completed - embedded: {}, skipped: {}, failed: {}, limit: {}",
                embeddedCount, skippedCount, failedCount, limit);
        return new RagEmbeddingBuildResult(embeddedCount, skippedCount, failedCount);
    }

    private List<RagDocumentForEmbedding> findDocumentsWithoutEmbedding(int limit) {
        String sql = """
                SELECT rd.id, rd.content
                FROM rag_document rd
                WHERE rd.content IS NOT NULL
                AND rd.content <> ''
                AND NOT EXISTS (
                    SELECT 1
                    FROM rag_embedding re
                    WHERE re.document_id = rd.id
                )
                ORDER BY rd.id
                """;

        if (limit > 0) {
            return jdbcTemplate.query(sql + " LIMIT ?", (rs, rowNum) ->
                    new RagDocumentForEmbedding(rs.getLong("id"), rs.getString("content")), limit);
        }

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new RagDocumentForEmbedding(rs.getLong("id"), rs.getString("content")));
    }

    private int insertEmbedding(Long documentId, List<Double> embedding) {
        return jdbcTemplate.update("""
                        INSERT INTO rag_embedding (document_id, embedding)
                        VALUES (?, ?::vector)
                        ON CONFLICT (document_id) DO NOTHING
                        """,
                documentId,
                RagVectorLiteralFormatter.toVectorLiteral(embedding));
    }

    private record RagDocumentForEmbedding(
            Long id,
            String content
    ) {
    }
}
