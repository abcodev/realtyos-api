package realtyos.server.application.rag.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import realtyos.server.application.common.entity.BaseEntity;
import realtyos.server.application.rag.domain.RagSearchCondition;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_ai_memory")
public class UserAiMemoryJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "preferred_region", length = 100)
    private String preferredRegion;

    @Column(name = "recent_region", length = 100)
    private String recentRegion;

    @Column(name = "min_price")
    private Long minPrice;

    @Column(name = "max_price")
    private Long maxPrice;

    @Column(name = "query_count", nullable = false)
    private Long queryCount = 0L;

    @Column(name = "last_query", columnDefinition = "TEXT")
    private String lastQuery;

    public static UserAiMemoryJpaEntity create(Long userId) {
        UserAiMemoryJpaEntity entity = new UserAiMemoryJpaEntity();
        entity.userId = userId;
        entity.queryCount = 0L;
        return entity;
    }

    public void record(String query, RagSearchCondition condition, String frequentRegion) {
        this.queryCount = this.queryCount == null ? 1L : this.queryCount + 1;
        this.lastQuery = query;

        if (condition == null) {
            if (hasText(frequentRegion)) {
                this.preferredRegion = frequentRegion;
            }
            return;
        }
        if (hasText(condition.region())) {
            this.recentRegion = condition.region();
        }
        if (hasText(frequentRegion)) {
            this.preferredRegion = frequentRegion;
        } else if (hasText(condition.region())) {
            this.preferredRegion = condition.region();
        }
        if (condition.minPrice() != null) {
            this.minPrice = condition.minPrice();
        }
        if (condition.maxPrice() != null) {
            this.maxPrice = condition.maxPrice();
        }
    }

    public UserAiMemory toDomain() {
        return new UserAiMemory(
                userId,
                preferredRegion,
                recentRegion,
                minPrice,
                maxPrice,
                queryCount == null ? 0 : queryCount,
                lastQuery
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
