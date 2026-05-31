package realtyos.server.application.rag.infrastructure.jpa.entity;

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
import realtyos.server.application.rag.domain.UserAiMemory;

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

    public static UserAiMemoryJpaEntity from(UserAiMemory memory) {
        UserAiMemoryJpaEntity entity = new UserAiMemoryJpaEntity();
        entity.userId = memory.userId();
        entity.apply(memory);
        return entity;
    }

    public void apply(UserAiMemory memory) {
        this.preferredRegion = memory.preferredRegion();
        this.recentRegion = memory.recentRegion();
        this.minPrice = memory.minPrice();
        this.maxPrice = memory.maxPrice();
        this.queryCount = memory.queryCount();
        this.lastQuery = memory.lastQuery();
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
}
