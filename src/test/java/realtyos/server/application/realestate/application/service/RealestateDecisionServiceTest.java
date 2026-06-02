package realtyos.server.application.realestate.application.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealestateDecisionServiceTest {

    @Test
    void doesNotTreatSingleRegionCandidateRecommendationAsMultiTargetComparison() throws Exception {
        RealestateDecisionService service = new RealestateDecisionService(null, null, null, null);
        Method method = RealestateDecisionService.class.getDeclaredMethod("inferComparisonTargets", String.class);
        method.setAccessible(true);

        Object result = method.invoke(service, "마포에서 갈아타기 후보를 비교해줘");

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).isEmpty();
    }
}
