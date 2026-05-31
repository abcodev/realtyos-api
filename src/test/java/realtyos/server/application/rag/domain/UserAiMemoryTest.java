package realtyos.server.application.rag.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAiMemoryTest {

    @Test
    void fillsMissingSearchConditionFromMemory() {
        UserAiMemory memory = new UserAiMemory(1L, "강남", "서초", 100000L, 150000L, 3, "강남 10억대");
        RagSearchCondition explicit = new RagSearchCondition(
                null, null, null, null, null, null, null, null, null, null, null);

        RagSearchCondition merged = memory.mergeInto(explicit);

        assertThat(merged.region()).isEqualTo("강남");
        assertThat(merged.minPrice()).isEqualTo(100000L);
        assertThat(merged.maxPrice()).isEqualTo(150000L);
    }

    @Test
    void explicitSearchConditionHasPriorityOverMemory() {
        UserAiMemory memory = new UserAiMemory(1L, "강남", null, 100000L, 150000L, 3, "강남 10억대");
        RagSearchCondition explicit = new RagSearchCondition(
                "서초", null, null, null, null, null, 120000L, null, null, null, null);

        RagSearchCondition merged = memory.mergeInto(explicit);

        assertThat(merged.region()).isEqualTo("서초");
        assertThat(merged.minPrice()).isEqualTo(120000L);
        assertThat(merged.maxPrice()).isEqualTo(150000L);
    }
}
