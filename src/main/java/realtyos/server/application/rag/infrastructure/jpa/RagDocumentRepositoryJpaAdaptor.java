package realtyos.server.application.rag.infrastructure.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import realtyos.server.application.rag.domain.EmbeddingModelProfile;
import realtyos.server.application.rag.domain.RagDocumentForEmbedding;
import realtyos.server.application.rag.domain.RagDocumentRepository;
import realtyos.server.application.rag.domain.RagEmbeddingToSave;
import realtyos.server.application.rag.domain.RagIndexStats;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.rag.domain.RagSearchResult;
import realtyos.server.application.realestate.domain.RegionResolution;
import realtyos.server.application.realestate.domain.RegionResolver;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Repository
@RequiredArgsConstructor
public class RagDocumentRepositoryJpaAdaptor implements RagDocumentRepository {

    private static final String DEAL_SOURCE_TYPE = "DEAL";

    private final JdbcTemplate jdbcTemplate;
    private final RegionResolver regionResolver;

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
                            concat('지역키워드: ', concat_ws(' ', nullif(d.sgg_code, ''), nullif(d.umd_name, ''))),
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
    public int saveEmbeddings(EmbeddingModelProfile profile, List<RagEmbeddingToSave> embeddings) {
        if (embeddings.isEmpty()) {
            return 0;
        }

        int[] updateCounts = jdbcTemplate.batchUpdate("""
                        INSERT INTO rag_embedding (document_id, provider, model, dimension, embedding)
                        VALUES (?, ?, ?, ?, ?::vector)
                        ON CONFLICT (document_id, provider, model) DO NOTHING
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        RagEmbeddingToSave embeddingToSave = embeddings.get(i);
                        ps.setLong(1, embeddingToSave.documentId());
                        ps.setString(2, profile.provider().name());
                        ps.setString(3, profile.model());
                        ps.setInt(4, embeddingToSave.embedding().size());
                        ps.setString(5, RagVectorLiteralFormatter.toVectorLiteral(embeddingToSave.embedding()));
                    }

                    @Override
                    public int getBatchSize() {
                        return embeddings.size();
                    }
                });

        int savedCount = 0;
        for (int updateCount : updateCounts) {
            if (updateCount > 0 || updateCount == Statement.SUCCESS_NO_INFO) {
                savedCount++;
            }
        }
        return savedCount;
    }

    @Override
    public List<RagSearchResult> searchByEmbedding(
            EmbeddingModelProfile profile,
            List<Double> embedding,
            int topK,
            RagSearchCondition condition
    ) {
        String queryVector = RagVectorLiteralFormatter.toVectorLiteral(embedding);
        List<Object> args = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
                WITH scored AS (
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
                        make_date(d.deal_year, d.deal_month, d.deal_day) AS deal_date,
                        d.exclu_use_area,
                        NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint AS deal_amount,
                        d.floor,
                        d.build_year,
                        (re.embedding <=> ?::vector) AS distance,
                        GREATEST(0.0, 1.0 - (re.embedding <=> ?::vector)) AS similarity,
                        CASE
                            WHEN d.deal_year IS NULL OR d.deal_month IS NULL OR d.deal_day IS NULL THEN 0.0
                            ELSE GREATEST(0.0, 1.0 - (CURRENT_DATE - make_date(d.deal_year, d.deal_month, d.deal_day))::double precision / 365.0)
                        END AS recency_score
                    FROM rag_embedding re
                    JOIN rag_document rd ON rd.id = re.document_id
                    LEFT JOIN real_estate_deals d ON rd.source_type = 'DEAL' AND rd.source_id = d.id
                    WHERE re.provider = ?
                    AND re.model = ?
                    AND re.dimension = ?
                """);

        args.add(queryVector);
        args.add(queryVector);
        args.add(profile.provider().name());
        args.add(profile.model());
        args.add(embedding.size());

        appendFilters(sql, args, condition);

        boolean recentFirst = condition != null && Boolean.TRUE.equals(condition.recentFirst());
        sql.append("""
                )
                SELECT
                    document_id,
                    embedding_provider,
                    embedding_model,
                    title,
                    content,
                    apartment_name,
                    region,
                    source_type,
                    source_id,
                    deal_date,
                    exclu_use_area,
                    deal_amount,
                    floor,
                    build_year,
                    distance,
                    similarity,
                    recency_score,
                    CASE
                        WHEN ? THEN similarity * 0.70 + recency_score * 0.30
                        ELSE similarity
                    END AS final_score
                FROM scored
                ORDER BY final_score DESC, deal_date DESC NULLS LAST
                LIMIT ?
                """);
        args.add(recentFirst);
        args.add(topK);

        return jdbcTemplate.query(sql.toString(),
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
                            "VECTOR",
                            rs.getString("deal_date"),
                            rs.getString("exclu_use_area"),
                            getNullableLong(rs, "deal_amount"),
                            rs.getString("floor"),
                            rs.getString("build_year"),
                            distance,
                            rs.getDouble("similarity"),
                            rs.getDouble("recency_score"),
                            rs.getDouble("final_score")
                    );
                },
                args.toArray());
    }

    @Override
    public List<RagSearchResult> searchDealsByKeyword(int topK, RagSearchCondition condition) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                WITH matched AS (
                    SELECT
                        d.id AS source_id,
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
                            '검색방식: keyword fallback',
                            concat('아파트명: ', coalesce(nullif(d.apt_name, ''), '정보없음')),
                            concat('법정동: ', coalesce(nullif(d.umd_name, ''), '정보없음')),
                            concat('지역코드: ', coalesce(nullif(d.sgg_code, ''), '정보없음')),
                            concat('거래일: ', coalesce(d.deal_year::text, '연도미상'), '-', lpad(coalesce(d.deal_month::text, '0'), 2, '0'), '-', lpad(coalesce(d.deal_day::text, '0'), 2, '0')),
                            concat('전용면적: ', coalesce(nullif(d.exclu_use_area, ''), '정보없음'), '㎡'),
                            concat('거래금액: ', coalesce(nullif(d.deal_amount, ''), '정보없음'), '만원'),
                            concat('층: ', coalesce(nullif(d.floor, ''), '정보없음')),
                            concat('건축년도: ', coalesce(nullif(d.build_year, ''), '정보없음')),
                            concat('거래유형: ', coalesce(nullif(d.dealing_type, ''), '정보없음')),
                            concat('중개사소재지: ', coalesce(nullif(d.estate_agent_sgg_name, ''), '정보없음')),
                            concat('실거래 원본 ID: ', d.id::text)
                        ) AS content,
                        nullif(d.apt_name, '') AS apartment_name,
                        concat_ws(' ', nullif(d.sgg_code, ''), nullif(d.umd_name, '')) AS region,
                        make_date(d.deal_year, d.deal_month, d.deal_day) AS deal_date,
                        d.exclu_use_area,
                        NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint AS deal_amount,
                        d.floor,
                        d.build_year,
                        CASE
                            WHEN d.deal_year IS NULL OR d.deal_month IS NULL OR d.deal_day IS NULL THEN 0.0
                            ELSE GREATEST(0.0, 1.0 - (CURRENT_DATE - make_date(d.deal_year, d.deal_month, d.deal_day))::double precision / 365.0)
                        END AS recency_score
                    FROM real_estate_deals d
                    WHERE d.id IS NOT NULL
                """);

        appendDealFilters(sql, args, condition);

        boolean recentFirst = condition != null && Boolean.TRUE.equals(condition.recentFirst());
        sql.append("""
                )
                SELECT
                    source_id,
                    title,
                    content,
                    apartment_name,
                    region,
                    deal_date,
                    exclu_use_area,
                    deal_amount,
                    floor,
                    build_year,
                    recency_score,
                    CASE WHEN ? THEN 0.70 + recency_score * 0.30 ELSE 0.70 END AS final_score
                FROM matched
                ORDER BY final_score DESC, deal_date DESC NULLS LAST
                LIMIT ?
                """);
        args.add(recentFirst);
        args.add(topK);

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new RagSearchResult(
                        null,
                        "KEYWORD",
                        "real_estate_deals",
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("apartment_name"),
                        rs.getString("region"),
                        DEAL_SOURCE_TYPE,
                        rs.getLong("source_id"),
                        "KEYWORD_FALLBACK",
                        rs.getString("deal_date"),
                        rs.getString("exclu_use_area"),
                        getNullableLong(rs, "deal_amount"),
                        rs.getString("floor"),
                        rs.getString("build_year"),
                        0.30,
                        0.70,
                        rs.getDouble("recency_score"),
                        rs.getDouble("final_score")
                ),
                args.toArray());
    }

    @Override
    public RagIndexStats getIndexStats(EmbeddingModelProfile profile) {
        Long documentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM rag_document", Long.class);
        Long changedDocumentCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM rag_document
                WHERE content_hash <> md5(content)
                """, Long.class);
        Long missingEmbeddingCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM rag_document rd
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM rag_embedding re
                    WHERE re.document_id = rd.id
                    AND re.provider = ?
                    AND re.model = ?
                )
                """, Long.class, profile.provider().name(), profile.model());
        List<RagIndexStats.EmbeddingStats> embeddings = jdbcTemplate.query("""
                        SELECT provider, model, dimension, count(*) AS count
                        FROM rag_embedding
                        GROUP BY provider, model, dimension
                        ORDER BY provider, model, dimension
                        """,
                (rs, rowNum) -> new RagIndexStats.EmbeddingStats(
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getInt("dimension"),
                        rs.getLong("count")
                ));

        return new RagIndexStats(
                documentCount == null ? 0 : documentCount,
                changedDocumentCount == null ? 0 : changedDocumentCount,
                missingEmbeddingCount == null ? 0 : missingEmbeddingCount,
                embeddings
        );
    }

    private void appendFilters(StringBuilder sql, List<Object> args, RagSearchCondition condition) {
        if (condition == null) {
            return;
        }
        if (hasText(condition.region())) {
            appendActualRegionFilter(sql, args, condition.region(), true);
        }
        if (hasText(condition.apartmentName())) {
            sql.append(" AND (rd.apartment_name ILIKE ? OR d.apt_name ILIKE ?) ");
            String value = like(condition.apartmentName());
            args.add(value);
            args.add(value);
        }

        LocalDate fromDate = toStartDate(condition.fromYear(), condition.fromMonth());
        if (fromDate != null) {
            sql.append(" AND make_date(d.deal_year, d.deal_month, d.deal_day) >= ? ");
            args.add(Date.valueOf(fromDate));
        }

        LocalDate toDate = toEndDate(condition.toYear(), condition.toMonth());
        if (toDate != null) {
            sql.append(" AND make_date(d.deal_year, d.deal_month, d.deal_day) <= ? ");
            args.add(Date.valueOf(toDate));
        }
        if (condition.minPrice() != null) {
            sql.append(" AND NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint >= ? ");
            args.add(condition.minPrice());
        }
        if (condition.maxPrice() != null) {
            sql.append(" AND NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint <= ? ");
            args.add(condition.maxPrice());
        }
        if (condition.minArea() != null) {
            sql.append(" AND NULLIF(d.exclu_use_area, '')::double precision >= ? ");
            args.add(condition.minArea());
        }
        if (condition.maxArea() != null) {
            sql.append(" AND NULLIF(d.exclu_use_area, '')::double precision <= ? ");
            args.add(condition.maxArea());
        }
    }

    private void appendDealFilters(StringBuilder sql, List<Object> args, RagSearchCondition condition) {
        if (condition == null) {
            return;
        }
        if (hasText(condition.region())) {
            appendActualRegionFilter(sql, args, condition.region(), false);
        }
        if (hasText(condition.apartmentName())) {
            sql.append(" AND d.apt_name ILIKE ? ");
            args.add(like(condition.apartmentName()));
        }

        LocalDate fromDate = toStartDate(condition.fromYear(), condition.fromMonth());
        if (fromDate != null) {
            sql.append(" AND make_date(d.deal_year, d.deal_month, d.deal_day) >= ? ");
            args.add(Date.valueOf(fromDate));
        }

        LocalDate toDate = toEndDate(condition.toYear(), condition.toMonth());
        if (toDate != null) {
            sql.append(" AND make_date(d.deal_year, d.deal_month, d.deal_day) <= ? ");
            args.add(Date.valueOf(toDate));
        }
        if (condition.minPrice() != null) {
            sql.append(" AND NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint >= ? ");
            args.add(condition.minPrice());
        }
        if (condition.maxPrice() != null) {
            sql.append(" AND NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint <= ? ");
            args.add(condition.maxPrice());
        }
        if (condition.minArea() != null) {
            sql.append(" AND NULLIF(d.exclu_use_area, '')::double precision >= ? ");
            args.add(condition.minArea());
        }
        if (condition.maxArea() != null) {
            sql.append(" AND NULLIF(d.exclu_use_area, '')::double precision <= ? ");
            args.add(condition.maxArea());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private void appendActualRegionFilter(StringBuilder sql, List<Object> args, String region, boolean includeDocumentRegion) {
        appendResolvedRegionFilter(sql, args, regionResolver.resolve(region), includeDocumentRegion);
    }

    private void appendResolvedRegionFilter(
            StringBuilder sql,
            List<Object> args,
            RegionResolution region,
            boolean includeDocumentRegion
    ) {
        if (region == null || !region.hasFilter()) {
            return;
        }

        switch (region.type()) {
            case SGG -> {
                sql.append(" AND d.sgg_code IN (")
                        .append(placeholders(region.sggCodes().size()))
                        .append(") ");
                args.addAll(region.sggCodes());
            }
            case DONG -> {
                sql.append(" AND d.umd_name ILIKE ? ");
                args.add(like(region.dongName()));
            }
            case KEYWORD -> appendKeywordRegionFilter(sql, args, region.keyword(), includeDocumentRegion);
            case NONE -> {
            }
        }
    }

    private void appendKeywordRegionFilter(
            StringBuilder sql,
            List<Object> args,
            String keyword,
            boolean includeDocumentRegion
    ) {
        if (includeDocumentRegion) {
            sql.append("""
                 AND (
                    rd.region ILIKE ?
                    OR d.umd_name IN (?, ?)
                    OR d.umd_name ILIKE ?
                    OR d.sgg_code ILIKE ?
                    OR d.sgg_code IN (
                        SELECT DISTINCT substring(b.bgd_code, 1, 5)
                        FROM real_estate_bgd_code b
                        WHERE (
                            b.bgd_name ILIKE ?
                            OR b.bgd_name ILIKE ?
                        )
                        AND b.bgd_code NOT LIKE '__00000000'
                    )
                 )
                """);
            String value = like(keyword);
            args.add(value);
            args.add(keyword);
            args.add(keyword + "동");
            args.add(value);
            args.add(value);
            args.add(value);
            args.add(like(keyword + "동"));
            return;
        }

        sql.append("""
                 AND (
                    d.umd_name IN (?, ?)
                    OR d.umd_name ILIKE ?
                    OR d.sgg_code ILIKE ?
                    OR d.sgg_code IN (
                        SELECT DISTINCT substring(b.bgd_code, 1, 5)
                        FROM real_estate_bgd_code b
                        WHERE (
                            b.bgd_name ILIKE ?
                            OR b.bgd_name ILIKE ?
                        )
                        AND b.bgd_code NOT LIKE '__00000000'
                    )
                 )
                """);
        String value = like(keyword);
        args.add(keyword);
        args.add(keyword + "동");
        args.add(value);
        args.add(value);
        args.add(value);
        args.add(like(keyword + "동"));
    }

    private String placeholders(int count) {
        return "?,".repeat(count).replaceAll(",$", "");
    }

    private LocalDate toStartDate(Integer year, Integer month) {
        if (year == null) {
            return null;
        }
        return YearMonth.of(year, month == null ? 1 : month).atDay(1);
    }

    private LocalDate toEndDate(Integer year, Integer month) {
        if (year == null) {
            return null;
        }
        YearMonth yearMonth = YearMonth.of(year, month == null ? 12 : month);
        return yearMonth.atEndOfMonth();
    }

    private Long getNullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

}
