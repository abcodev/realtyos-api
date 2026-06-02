package realtyos.server.application.realestate.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionResolutionTest {

    @Test
    void createsSggResolution() {
        RegionResolution resolution = RegionResolution.sgg("마포", List.of("11440"));

        assertThat(resolution.type()).isEqualTo(RegionResolutionType.SGG);
        assertThat(resolution.sggCodes()).containsExactly("11440");
        assertThat(resolution.hasFilter()).isTrue();
    }

    @Test
    void createsDongResolution() {
        RegionResolution resolution = RegionResolution.dong("개포동", "개포동");

        assertThat(resolution.type()).isEqualTo(RegionResolutionType.DONG);
        assertThat(resolution.dongName()).isEqualTo("개포동");
    }
}
