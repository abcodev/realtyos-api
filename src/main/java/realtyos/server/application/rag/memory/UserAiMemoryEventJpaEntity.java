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
import realtyos.server.application.rag.domain.RagSearchCondition;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_ai_memory_event")
public class UserAiMemoryEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "query", nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "apartment_name")
    private String apartmentName;

    @Column(name = "min_price")
    private Long minPrice;

    @Column(name = "max_price")
    private Long maxPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static UserAiMemoryEventJpaEntity create(Long userId, String query, RagSearchCondition condition) {
        UserAiMemoryEventJpaEntity entity = new UserAiMemoryEventJpaEntity();
        entity.userId = userId;
        entity.query = query;
        if (condition != null) {
            entity.region = blankToNull(condition.region());
            entity.apartmentName = blankToNull(condition.apartmentName());
            entity.minPrice = condition.minPrice();
            entity.maxPrice = condition.maxPrice();
        }
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
