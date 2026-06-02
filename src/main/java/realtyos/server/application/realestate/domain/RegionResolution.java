package realtyos.server.application.realestate.domain;

import java.util.List;

public record RegionResolution(
        String input,
        RegionResolutionType type,
        List<String> sggCodes,
        String dongName,
        String keyword
) {

    public static RegionResolution empty(String input) {
        return new RegionResolution(input, RegionResolutionType.NONE, List.of(), null, null);
    }

    public static RegionResolution sgg(String input, List<String> sggCodes) {
        return new RegionResolution(input, RegionResolutionType.SGG, List.copyOf(sggCodes), null, null);
    }

    public static RegionResolution dong(String input, String dongName) {
        return new RegionResolution(input, RegionResolutionType.DONG, List.of(), dongName, null);
    }

    public static RegionResolution keyword(String input, String keyword) {
        return new RegionResolution(input, RegionResolutionType.KEYWORD, List.of(), null, keyword);
    }

    public boolean hasFilter() {
        return type != RegionResolutionType.NONE;
    }
}
