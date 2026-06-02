package realtyos.server.application.rag.domain;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RagQueryRewritePolicy {

    private static final Pattern EOK_PRICE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*억\\s*(이상|초과|부터|이하|미만|까지|대)?");
    private static final Pattern MANWON_PRICE_PATTERN = Pattern.compile("(\\d{4,})\\s*만\\s*원?\\s*(이상|초과|부터|이하|미만|까지)?");
    private static final Pattern AREA_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:제곱|평방|㎡|m2|m\\^2)");
    private static final Pattern PYEONG_RANGE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*평\\s*대");
    private static final Pattern PYEONG_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*평(?:형)?");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(20\\d{2})\\s*년(?:\\s*(\\d{1,2})\\s*월)?");
    private static final double SQUARE_METERS_PER_PYEONG = 3.305785;

    private static final List<String> REGION_KEYWORDS = List.of(
            "강남구", "강남",
            "서초구", "서초",
            "송파구", "송파",
            "마포구", "마포",
            "용산구", "용산",
            "성동구", "성동",
            "영등포구", "영등포",
            "분당구", "분당",
            "판교",
            "잠실동", "잠실",
            "반포",
            "압구정",
            "대치동", "대치",
            "목동"
    );
    private static final Map<String, String> REGION_CANONICAL_NAMES = Map.ofEntries(
            Map.entry("강남구", "강남구"),
            Map.entry("강남", "강남구"),
            Map.entry("서초구", "서초구"),
            Map.entry("서초", "서초구"),
            Map.entry("송파구", "송파구"),
            Map.entry("송파", "송파구"),
            Map.entry("마포구", "마포구"),
            Map.entry("마포", "마포구"),
            Map.entry("용산구", "용산구"),
            Map.entry("용산", "용산구"),
            Map.entry("성동구", "성동구"),
            Map.entry("성동", "성동구"),
            Map.entry("영등포구", "영등포구"),
            Map.entry("영등포", "영등포구"),
            Map.entry("분당구", "분당구"),
            Map.entry("분당", "분당구"),
            Map.entry("판교", "판교"),
            Map.entry("잠실동", "잠실동"),
            Map.entry("잠실", "잠실동"),
            Map.entry("반포", "반포동"),
            Map.entry("압구정", "압구정동"),
            Map.entry("대치동", "대치동"),
            Map.entry("대치", "대치동"),
            Map.entry("목동", "목동")
    );

    public RagQueryRewriteResult rewrite(String query, RagSearchCondition explicitCondition) {
        RagSearchCondition inferred = infer(query);
        RagSearchCondition merged = merge(explicitCondition, inferred);
        return new RagQueryRewriteResult(rewriteQuery(query, merged), merged);
    }

    public List<String> inferComparisonRegions(String query) {
        String text = query == null ? "" : query;
        if (!containsAny(text, "비교", "차이", "대비", " vs ", "VS", "와", "과", "랑", "하고")) {
            return List.of();
        }
        LinkedHashMap<String, String> regions = new LinkedHashMap<>();
        REGION_KEYWORDS.stream()
                .filter(text::contains)
                .forEach(keyword -> regions.putIfAbsent(canonicalRegion(keyword), canonicalRegion(keyword)));
        return regions.size() > 1 ? List.copyOf(regions.values()) : List.of();
    }

    private RagSearchCondition infer(String query) {
        String text = query == null ? "" : query;
        String normalized = text.replace(",", "");

        String region = inferRegion(normalized);
        Boolean recentFirst = containsAny(normalized, "최근", "최신", "요즘", "근래", "최근순") ? Boolean.TRUE : null;

        PriceRange priceRange = inferPriceRange(normalized);
        AreaRange areaRange = inferAreaRange(normalized);
        YearMonthRange yearMonthRange = inferYearMonthRange(normalized);

        return new RagSearchCondition(
                region,
                null,
                yearMonthRange.fromYear(),
                yearMonthRange.fromMonth(),
                yearMonthRange.toYear(),
                yearMonthRange.toMonth(),
                priceRange.minPrice(),
                priceRange.maxPrice(),
                areaRange.minArea(),
                areaRange.maxArea(),
                recentFirst
        );
    }

    private String rewriteQuery(String query, RagSearchCondition condition) {
        StringBuilder rewritten = new StringBuilder(query == null ? "" : query.trim());
        if (!containsAny(rewritten.toString(), "실거래", "실거래가")) {
            rewritten.append(" 실거래가");
        }
        if (!containsAny(rewritten.toString(), "아파트")) {
            rewritten.append(" 아파트");
        }
        if (condition.region() != null && !rewritten.toString().contains(condition.region())) {
            rewritten.append(' ').append(condition.region());
        }
        return rewritten.toString().trim();
    }

    private RagSearchCondition merge(RagSearchCondition explicit, RagSearchCondition inferred) {
        if (explicit == null) {
            return inferred;
        }
        return new RagSearchCondition(
                coalesce(explicit.region(), inferred.region()),
                coalesce(explicit.apartmentName(), inferred.apartmentName()),
                coalesce(explicit.fromYear(), inferred.fromYear()),
                coalesce(explicit.fromMonth(), inferred.fromMonth()),
                coalesce(explicit.toYear(), inferred.toYear()),
                coalesce(explicit.toMonth(), inferred.toMonth()),
                coalesce(explicit.minPrice(), inferred.minPrice()),
                coalesce(explicit.maxPrice(), inferred.maxPrice()),
                coalesce(explicit.minArea(), inferred.minArea()),
                coalesce(explicit.maxArea(), inferred.maxArea()),
                coalesce(explicit.recentFirst(), inferred.recentFirst())
        );
    }

    private String inferRegion(String text) {
        List<String> matchedRegions = REGION_KEYWORDS.stream()
                .filter(text::contains)
                .toList();
        if (matchedRegions.size() > 1 && containsAny(text, "비교", "차이", "대비", " vs ", "VS", "와", "과", "랑", "하고")) {
            return null;
        }
        return matchedRegions.stream().findFirst().orElse(null);
    }

    private String canonicalRegion(String region) {
        return REGION_CANONICAL_NAMES.getOrDefault(region, region);
    }

    private PriceRange inferPriceRange(String text) {
        Matcher eokMatcher = EOK_PRICE_PATTERN.matcher(text);
        if (eokMatcher.find()) {
            long price = Math.round(Double.parseDouble(eokMatcher.group(1)) * 10000);
            return toPriceRange(price, eokMatcher.group(2), true);
        }

        Matcher manwonMatcher = MANWON_PRICE_PATTERN.matcher(text);
        if (manwonMatcher.find()) {
            long price = Long.parseLong(manwonMatcher.group(1));
            return toPriceRange(price, manwonMatcher.group(2), false);
        }

        return new PriceRange(null, null);
    }

    private PriceRange toPriceRange(long price, String qualifier, boolean eokExpression) {
        if (qualifier == null || qualifier.isBlank()) {
            if (eokExpression) {
                return new PriceRange(price, price + 9999);
            }
            return new PriceRange(price, null);
        }
        return switch (qualifier) {
            case "이상", "초과", "부터" -> new PriceRange(price, null);
            case "이하", "미만", "까지" -> new PriceRange(null, price);
            case "대" -> new PriceRange(price, price + 9999);
            default -> new PriceRange(null, null);
        };
    }

    private AreaRange inferAreaRange(String text) {
        Matcher pyeongRangeMatcher = PYEONG_RANGE_PATTERN.matcher(text);
        if (pyeongRangeMatcher.find()) {
            double pyeong = Double.parseDouble(pyeongRangeMatcher.group(1));
            return new AreaRange(toSquareMeters(pyeong), toSquareMeters(pyeong + 10));
        }

        Matcher pyeongMatcher = PYEONG_PATTERN.matcher(text);
        if (pyeongMatcher.find()) {
            double area = toSquareMeters(Double.parseDouble(pyeongMatcher.group(1)));
            return new AreaRange(Math.max(0, area - 6), area + 6);
        }

        Matcher matcher = AREA_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new AreaRange(null, null);
        }
        double area = Double.parseDouble(matcher.group(1));
        return new AreaRange(Math.max(0, area - 5), area + 5);
    }

    private double toSquareMeters(double pyeong) {
        return Math.round(pyeong * SQUARE_METERS_PER_PYEONG * 100.0) / 100.0;
    }

    private YearMonthRange inferYearMonthRange(String text) {
        if (text.contains("올해")) {
            int currentYear = Year.now().getValue();
            return new YearMonthRange(currentYear, 1, currentYear, 12);
        }
        if (text.contains("작년")) {
            int lastYear = Year.now().getValue() - 1;
            return new YearMonthRange(lastYear, 1, lastYear, 12);
        }

        Matcher matcher = YEAR_MONTH_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new YearMonthRange(null, null, null, null);
        }

        int year = Integer.parseInt(matcher.group(1));
        String monthGroup = matcher.group(2);
        if (monthGroup == null) {
            return new YearMonthRange(year, 1, year, 12);
        }

        int month = Integer.parseInt(monthGroup);
        return new YearMonthRange(year, month, year, month);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private <T> T coalesce(T explicit, T inferred) {
        if (explicit instanceof String explicitText && explicitText.isBlank()) {
            return inferred;
        }
        return explicit != null ? explicit : inferred;
    }

    private record PriceRange(Long minPrice, Long maxPrice) {
    }

    private record AreaRange(Double minArea, Double maxArea) {
    }

    private record YearMonthRange(Integer fromYear, Integer fromMonth, Integer toYear, Integer toMonth) {
    }
}
