package realestate.server.application.rag.infrastructure.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import realestate.server.application.rag.domain.EmbeddingModelProfile;
import realestate.server.application.rag.domain.RagDocumentForEmbedding;
import realestate.server.application.rag.domain.RagDocumentRepository;
import realestate.server.application.rag.domain.RagSearchResult;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RagDocumentRepositoryJpaAdaptor implements RagDocumentRepository {

    private static final String DEAL_SOURCE_TYPE = "DEAL";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int buildDealDocuments(int limit) {
        String sql = """
                WITH source_deals AS (
                    SELECT d.*
                    FROM real_estate_deals d
                    WHERE d.id IS NOT NULL
                    ORDER BY d.id
                    LIMIT NULLIF(?, 0)
                ),
                prepared_documents AS (
                    SELECT
                        concat_ws(' ',
                            coalesce(nullif(d.apt_name, ''), '아파트명 미상'),
                            coalesce(nullif(d.umd_name, ''), ''),
                            '아파트 실거래',
                            concat(
                                coalesce(d.deal_year::text, '연도미상'), '년 ',
                                coalesce(d.deal_month::text, '월미상'), '월 ',
                                coalesce(d.deal_day::text, '일미상'), '일'
                            )
                        ) AS title,
                        concat_ws(E'\\n',
                            '문서유형: 아파트 실거래가',
                            '검색키워드: 실거래가 아파트 매매 거래금액 거래일 전용면적 층 법정동 도로명',
                            concat('지역키워드: ', concat_ws(' ', nullif(d.sgg_code, ''), nullif(d.umd_name, ''), nullif(d.estate_agent_sgg_name, ''))),
                            concat('아파트키워드: ', coalesce(nullif(d.apt_name, ''), '정보없음'), ' ', coalesce(nullif(d.apt_seq, ''), '')),
                            concat('거래년월: ', coalesce(d.deal_year::text, '연도미상'), '-', lpad(coalesce(d.deal_month::text, '0'), 2, '0')),
                            concat('거래일: ', coalesce(d.deal_year::text, '연도미상'), '-', lpad(coalesce(d.deal_month::text, '0'), 2, '0'), '-', lpad(coalesce(d.deal_day::text, '0'), 2, '0')),
                            concat('아파트명: ', coalesce(nullif(d.apt_name, ''), '정보없음')),
                            concat('법정동: ', coalesce(nullif(d.umd_name, ''), '정보없음')),
                            concat('지번: ', coalesce(nullif(d.jibun, ''), '정보없음')),
                            concat('도로명: ', coalesce(nullif(d.road_name, ''), '정보없음')),
                            concat('전용면적: ', coalesce(nullif(d.exclu_use_area, ''), '정보없음'), '㎡'),
                            concat('면적검색: 전용 ', coalesce(nullif(d.exclu_use_area, ''), '정보없음'), ' 제곱미터'),
                            concat('거래금액: ', coalesce(nullif(d.deal_amount, ''), '정보없음'), '만원'),
                            concat('가격검색: ', coalesce(nullif(d.deal_amount, ''), '정보없음'), '만원'),
                            concat('층: ', coalesce(nullif(d.floor, ''), '정보없음')),
                            concat('건축년도: ', coalesce(nullif(d.build_year, ''), '정보없음')),
                            concat('거래유형: ', coalesce(nullif(d.dealing_type, ''), '정보없음')),
                            concat('중개사소재지: ', coalesce(nullif(d.estate_agent_sgg_name, ''), '정보없음')),
                            concat('매도자유형: ', coalesce(nullif(d.sler_type, ''), '정보없음')),
                            concat('매수자유형: ', coalesce(nullif(d.buyer_type, ''), '정보없음')),
                            concat('토지임대부여부: ', coalesce(nullif(d.land_leasehold_type, ''), '정보없음')),
                            concat('실거래 원본 ID: ', d.id::text)
                        ) AS content,
                        nullif(d.apt_name, '') AS apartment_name,
                        concat_ws(' ', nullif(d.sgg_code, ''), nullif(d.umd_name, '')) AS region,
                        ? AS source_type,
                        d.id AS source_id
                    FROM source_deals d
                ),
                upserted_documents AS (
                INSERT INTO rag_document (
                    title,
                    content,
                    apartment_name,
                    region,
                    source_type,
                    source_id,
                    content_hash,
                    updated_at
                )
                SELECT
                    pd.title,
                    pd.content,
                    pd.apartment_name,
                    pd.region,
                    pd.source_type,
                    pd.source_id,
                    md5(pd.content),
                    now()
                FROM prepared_documents pd
                """;

        sql += """
                    ON CONFLICT (source_type, source_id)
                    DO UPDATE SET
                        title = EXCLUDED.title,
                        content = EXCLUDED.content,
                        apartment_name = EXCLUDED.apartment_name,
                        region = EXCLUDED.region,
                        content_hash = EXCLUDED.content_hash,
                        document_version = rag_document.document_version + 1,
                        updated_at = now()
                    WHERE rag_document.content_hash IS DISTINCT FROM EXCLUDED.content_hash
                    RETURNING id
                ),
                deleted_embeddings AS (
                    DELETE FROM rag_embedding re
                    USING upserted_documents ud
                    WHERE re.document_id = ud.id
                )
                SELECT count(*) FROM upserted_documents
                """;

        Integer upsertedCount = jdbcTemplate.queryForObject(sql, Integer.class, limit, DEAL_SOURCE_TYPE);

        return upsertedCount == null ? 0 : upsertedCount;
    }

    @Override
    public List<RagDocumentForEmbedding> findDocumentsWithoutEmbedding(EmbeddingModelProfile profile, int limit) {
        String sql = """
                SELECT rd.id, rd.content
                FROM rag_document rd
                WHERE rd.content IS NOT NULL
                AND rd.content <> ''
                AND NOT EXISTS (
                    SELECT 1
                    FROM rag_embedding re
                    WHERE re.document_id = rd.id
                    AND re.provider = ?
                    AND re.model = ?
                )
                ORDER BY rd.id
                """;

        if (limit > 0) {
            return jdbcTemplate.query(sql + " LIMIT ?", (rs, rowNum) ->
                            new RagDocumentForEmbedding(rs.getLong("id"), rs.getString("content")),
                    profile.provider().name(),
                    profile.model(),
                    limit);
        }

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                        new RagDocumentForEmbedding(rs.getLong("id"), rs.getString("content")),
                profile.provider().name(),
                profile.model());
    }

    @Override
    public int saveEmbedding(Long documentId, EmbeddingModelProfile profile, List<Double> embedding) {
        return jdbcTemplate.update("""
                        INSERT INTO rag_embedding (document_id, provider, model, dimension, embedding)
                        VALUES (?, ?, ?, ?, ?::vector)
                        ON CONFLICT (document_id, provider, model) DO NOTHING
                        """,
                documentId,
                profile.provider().name(),
                profile.model(),
                embedding.size(),
                RagVectorLiteralFormatter.toVectorLiteral(embedding));
    }

    @Override
    public List<RagSearchResult> searchByEmbedding(EmbeddingModelProfile profile, List<Double> embedding, int topK) {
        String queryVector = RagVectorLiteralFormatter.toVectorLiteral(embedding);

        return jdbcTemplate.query("""
                        SELECT
                            rd.id AS document_id,
                            re.provider AS embedding_provider,
                            re.model AS embedding_model,
                            rd.title,
                            rd.content,
                            rd.apartment_name,
                            rd.region,
                            rd.source_type,
                            rd.source_id,
                            (re.embedding <=> ?::vector) AS distance
                        FROM rag_embedding re
                        JOIN rag_document rd ON rd.id = re.document_id
                        WHERE re.provider = ?
                        AND re.model = ?
                        AND re.dimension = ?
                        ORDER BY re.embedding <=> ?::vector
                        LIMIT ?
                        """,
                (rs, rowNum) -> {
                    double distance = rs.getDouble("distance");
                    return new RagSearchResult(
                            rs.getLong("document_id"),
                            rs.getString("embedding_provider"),
                            rs.getString("embedding_model"),
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
                profile.provider().name(),
                profile.model(),
                embedding.size(),
                queryVector,
                topK);
    }
}
