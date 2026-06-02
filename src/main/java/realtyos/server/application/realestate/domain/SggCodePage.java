package realtyos.server.application.realestate.domain;

import java.util.List;

public record SggCodePage(
        List<SggCode> codes,
        int currentPage,
        int totalPages,
        boolean valid
) {

    public static SggCodePage invalid() {
        return new SggCodePage(List.of(), 0, 0, false);
    }
}
