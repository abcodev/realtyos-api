package realtyos.server.application.realestate.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import realtyos.server.application.rag.domain.RagSearchCondition;
import realtyos.server.application.realestate.domain.DecisionCandidate;
import realtyos.server.application.realestate.domain.DecisionDealSample;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DealAnalysisService {

    private static final Map<String, String> REGION_CODE_ALIASES = Map.ofEntries(
            Map.entry("서울", "11"),
            Map.entry("서울시", "11"),
            Map.entry("서울특별시", "11"),
            Map.entry("강남구", "11680"),
            Map.entry("강남", "11680"),
            Map.entry("서초구", "11650"),
            Map.entry("서초", "11650"),
            Map.entry("송파구", "11710"),
            Map.entry("송파", "11710"),
            Map.entry("마포구", "11440"),
            Map.entry("마포", "11440"),
            Map.entry("용산구", "11170"),
            Map.entry("용산", "11170"),
            Map.entry("성동구", "11200"),
            Map.entry("성동", "11200"),
            Map.entry("영등포구", "11560"),
            Map.entry("영등포", "11560"),
            Map.entry("양천구", "11470"),
            Map.entry("목동", "11470")
    );
    private static final Map<String, String> DONG_ALIASES = Map.ofEntries(
            Map.entry("대치동", "대치동"),
            Map.entry("대치", "대치동"),
            Map.entry("잠실동", "잠실동"),
            Map.entry("잠실", "잠실동"),
            Map.entry("반포동", "반포동"),
            Map.entry("반포", "반포동"),
            Map.entry("압구정동", "압구정동"),
            Map.entry("압구정", "압구정동"),
            Map.entry("목동", "목동")
    );

    private final JdbcTemplate jdbcTemplate;

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
        String normalizedRegion = region.trim();
        String regionCode = REGION_CODE_ALIASES.get(normalizedRegion);
        if (regionCode != null) {
            if (regionCode.length() == 2) {
                sql.append(" AND d.sgg_code LIKE ? ");
                args.add(regionCode + "%");
            } else {
                sql.append(" AND d.sgg_code = ? ");
                args.add(regionCode);
            }
            return;
        }
        String dongName = DONG_ALIASES.get(normalizedRegion);
        if (dongName != null) {
            sql.append(" AND d.umd_name ILIKE ? ");
            args.add(like(dongName));
            return;
        }
        sql.append(" AND (d.umd_name ILIKE ? OR d.sgg_code ILIKE ?) ");
        String value = like(normalizedRegion);
        args.add(value);
        args.add(value);
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
}
