package realtyos.server.application.realestate.infrastructure.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import realtyos.server.application.realestate.domain.RegionResolution;
import realtyos.server.application.realestate.domain.RegionResolver;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JdbcRegionResolver implements RegionResolver {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RegionResolution resolve(String region) {
        if (region == null || region.isBlank()) {
            return RegionResolution.empty(region);
        }

        String normalized = region.trim();
        if (isDongLevelRegion(normalized)) {
            return RegionResolution.dong(normalized, normalized);
        }

        List<String> sggCodes = findSggCodes(normalized);
        if (!sggCodes.isEmpty()) {
            return RegionResolution.sgg(normalized, sggCodes);
        }
        return RegionResolution.keyword(normalized, normalized);
    }

    private List<String> findSggCodes(String region) {
        return jdbcTemplate.queryForList("""
                SELECT s.sgg_cd
                FROM real_estate_sgg_code s
                WHERE s.sig_kor_nm IN (?, ?)
                OR s.full_nm ILIKE ?
                ORDER BY s.sgg_cd
                """, String.class, region, region + "구", "% " + region + "구");
    }

    private boolean isDongLevelRegion(String value) {
        return value.endsWith("동") || value.endsWith("읍") || value.endsWith("면") || value.endsWith("리");
    }
}
