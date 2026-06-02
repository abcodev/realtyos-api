package realtyos.server.application.realestate.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;
import realtyos.server.application.realestate.domain.DecisionScoreBreakdown;
import realtyos.server.application.realestate.domain.RegionResolution;
import realtyos.server.application.realestate.domain.RegionResolver;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DealAnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final RegionResolver regionResolver;

    public List<DecisionCandidate> findCandidates(RagSearchCondition condition, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                WITH max_deal_date AS (
                    SELECT max(make_date(deal_year, deal_month, deal_day)) AS max_date
                    FROM real_estate_deals
                    WHERE deal_year IS NOT NULL
                    AND deal_month IS NOT NULL
                    AND deal_day IS NOT NULL
                ),
                filtered AS (
                    SELECT
                        d.id,
                        d.apt_name,
                        d.sgg_code,
                        d.umd_name,
                        make_date(d.deal_year, d.deal_month, d.deal_day) AS deal_date,
                        NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint AS deal_amount,
                        NULLIF(d.exclu_use_area, '')::double precision AS exclusive_area,
                        d.floor
                    FROM real_estate_deals d
                    CROSS JOIN max_deal_date m
                    WHERE d.id IS NOT NULL
                    AND d.apt_name IS NOT NULL
                    AND d.apt_name <> ''
                    AND d.deal_year IS NOT NULL
                    AND d.deal_month IS NOT NULL
                    AND d.deal_day IS NOT NULL
                    AND NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '') IS NOT NULL
                    AND NULLIF(d.exclu_use_area, '') IS NOT NULL
                    AND make_date(d.deal_year, d.deal_month, d.deal_day) >= COALESCE(m.max_date - interval '12 months', CURRENT_DATE - interval '12 months')
                """);

        appendFilters(sql, args, condition);

        sql.append("""
                ),
                ranked AS (
                    SELECT
                        f.*,
                        row_number() over (
                            partition by f.sgg_code, f.umd_name, f.apt_name
                            order by f.deal_date desc, f.id desc
                        ) AS rn
                    FROM filtered f
                ),
                grouped AS (
                    SELECT
                        apt_name,
                        sgg_code,
                        umd_name,
                        max(deal_date) AS latest_deal_date,
                        max(deal_amount) filter (where rn = 1) AS latest_deal_amount,
                        min(deal_amount) AS min_deal_amount,
                        max(deal_amount) AS max_deal_amount,
                        round(avg(deal_amount))::bigint AS average_deal_amount,
                        min(exclusive_area) AS min_exclusive_area,
                        max(exclusive_area) AS max_exclusive_area,
                        avg(exclusive_area) AS average_exclusive_area,
                        count(*) AS deal_count,
                        round(avg(deal_amount / nullif(exclusive_area / 3.305785, 0)))::bigint AS average_price_per_pyeong
                    FROM ranked
                    GROUP BY apt_name, sgg_code, umd_name
                )
                SELECT *
                FROM grouped
                ORDER BY
                    deal_count DESC,
                    latest_deal_date DESC,
                    average_deal_amount ASC
                LIMIT ?
                """);
        args.add(Math.max(1, Math.min(30, limit)));

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String apartmentName = rs.getString("apt_name");
            String regionCode = rs.getString("sgg_code");
            String dongName = rs.getString("umd_name");
            return new DecisionCandidate(
                    apartmentName,
                    regionCode,
                    dongName,
                    rs.getString("latest_deal_date"),
                    getNullableLong(rs, "latest_deal_amount"),
                    getNullableLong(rs, "min_deal_amount"),
                    getNullableLong(rs, "max_deal_amount"),
                    getNullableLong(rs, "average_deal_amount"),
                    getNullableDouble(rs, "min_exclusive_area"),
                    getNullableDouble(rs, "max_exclusive_area"),
                    getNullableDouble(rs, "average_exclusive_area"),
                    getNullableLong(rs, "deal_count"),
                    getNullableLong(rs, "average_price_per_pyeong"),
                    0,
                    new DecisionScoreBreakdown(0, 0, 0, 0),
                    List.of(),
                    List.of(),
                    findSamples(regionCode, dongName, apartmentName, condition)
            );
        }, args.toArray());
    }

    private List<DecisionDealSample> findSamples(
            String regionCode,
            String dongName,
            String apartmentName,
            RagSearchCondition condition
    ) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT
                    d.id,
                    d.apt_name,
                    d.sgg_code,
                    d.umd_name,
                    make_date(d.deal_year, d.deal_month, d.deal_day) AS deal_date,
                    NULLIF(regexp_replace(d.deal_amount, '[^0-9]', '', 'g'), '')::bigint AS deal_amount,
                    NULLIF(d.exclu_use_area, '')::double precision AS exclusive_area,
                    d.floor
                FROM real_estate_deals d
                WHERE d.sgg_code = ?
                AND d.umd_name = ?
                AND d.apt_name = ?
                AND d.deal_year IS NOT NULL
                AND d.deal_month IS NOT NULL
                AND d.deal_day IS NOT NULL
                """);
        args.add(regionCode);
        args.add(dongName);
        args.add(apartmentName);
        appendNumericFilters(sql, args, condition);
        sql.append("""
                ORDER BY make_date(d.deal_year, d.deal_month, d.deal_day) DESC, d.id DESC
                LIMIT 3
                """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DecisionDealSample(
                rs.getLong("id"),
                rs.getString("apt_name"),
                rs.getString("sgg_code"),
                rs.getString("umd_name"),
                rs.getString("deal_date"),
                getNullableLong(rs, "deal_amount"),
                getNullableDouble(rs, "exclusive_area"),
                rs.getString("floor")
        ), args.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> args, RagSearchCondition condition) {
        if (condition == null) {
            return;
        }
        appendRegionFilter(sql, args, condition.region());
        if (hasText(condition.apartmentName())) {
            sql.append(" AND d.apt_name ILIKE ? ");
            args.add(like(condition.apartmentName()));
        }
        appendDateFilters(sql, args, condition);
        appendNumericFilters(sql, args, condition);
    }

    private void appendDateFilters(StringBuilder sql, List<Object> args, RagSearchCondition condition) {
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
    }

    private void appendNumericFilters(StringBuilder sql, List<Object> args, RagSearchCondition condition) {
        if (condition == null) {
            return;
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

    private void appendRegionFilter(StringBuilder sql, List<Object> args, String region) {
        if (!hasText(region)) {
            return;
        }
        appendResolvedRegionFilter(sql, args, regionResolver.resolve(region));
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
        return YearMonth.of(year, month == null ? 12 : month).atEndOfMonth();
    }

    private Long getNullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Double getNullableDouble(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void appendResolvedRegionFilter(StringBuilder sql, List<Object> args, RegionResolution region) {
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
            case KEYWORD -> {
                sql.append("""
                         AND (
                            d.umd_name IN (?, ?)
                            OR d.umd_name ILIKE ?
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
                args.add(region.keyword());
                args.add(region.keyword() + "동");
                args.add(like(region.keyword()));
                args.add(like(region.keyword()));
                args.add(like(region.keyword() + "동"));
            }
            case NONE -> {
            }
        }
    }

    private String placeholders(int count) {
        return "?,".repeat(count).replaceAll(",$", "");
    }
}
