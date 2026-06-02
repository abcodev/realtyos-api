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

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sources_json", columnDefinition = "TEXT")
    private String sourcesJson;

    @Column(name = "decision_json", columnDefinition = "TEXT")
    private String decisionJson;

    @Column(name = "model")
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static UserAiMemoryEventJpaEntity create(
            Long userId,
            String query,
            String region,
            String apartmentName,
            Long minPrice,
            Long maxPrice,
            String answer,
            String sourcesJson,
            String decisionJson,
            String model,
            LocalDateTime createdAt
    ) {
        UserAiMemoryEventJpaEntity entity = new UserAiMemoryEventJpaEntity();
        entity.userId = userId;
        entity.query = query;
        entity.region = region;
        entity.apartmentName = apartmentName;
        entity.minPrice = minPrice;
        entity.maxPrice = maxPrice;
        entity.answer = answer;
        entity.sourcesJson = sourcesJson;
        entity.decisionJson = decisionJson;
        entity.model = model;
        entity.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        return entity;
    }
}
