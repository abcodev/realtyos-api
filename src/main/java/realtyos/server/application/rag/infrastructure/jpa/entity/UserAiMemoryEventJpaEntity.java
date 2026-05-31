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
import realtyos.server.application.rag.domain.UserAiMemoryEvent;

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

    public static UserAiMemoryEventJpaEntity from(UserAiMemoryEvent event) {
        UserAiMemoryEventJpaEntity entity = new UserAiMemoryEventJpaEntity();
        entity.userId = event.userId();
        entity.query = event.query();
        entity.region = event.region();
        entity.apartmentName = event.apartmentName();
        entity.minPrice = event.minPrice();
        entity.maxPrice = event.maxPrice();
        entity.createdAt = event.createdAt();
        return entity;
    }

    public UserAiMemoryEvent toDomain() {
        return new UserAiMemoryEvent(
                id,
                userId,
                query,
                region,
                apartmentName,
                minPrice,
                maxPrice,
                createdAt
        );
    }
}
