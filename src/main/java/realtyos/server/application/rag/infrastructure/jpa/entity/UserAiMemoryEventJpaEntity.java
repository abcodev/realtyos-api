package realtyos.server.application.rag.infrastructure.jpa.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import realtyos.server.application.rag.domain.RagAnswerSource;
import realtyos.server.application.rag.domain.UserAiMemoryEvent;
import realtyos.server.application.realestate.domain.DecisionResult;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_ai_memory_event")
public class UserAiMemoryEventJpaEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<List<RagAnswerSource>> SOURCE_LIST_TYPE = new TypeReference<>() {
    };

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

    public static UserAiMemoryEventJpaEntity from(UserAiMemoryEvent event) {
        UserAiMemoryEventJpaEntity entity = new UserAiMemoryEventJpaEntity();
        entity.userId = event.userId();
        entity.query = event.query();
        entity.region = event.region();
        entity.apartmentName = event.apartmentName();
        entity.minPrice = event.minPrice();
        entity.maxPrice = event.maxPrice();
        entity.answer = event.answer();
        entity.sourcesJson = writeJson(event.sources());
        entity.decisionJson = writeJson(event.decision());
        entity.model = event.model();
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
                answer,
                readSources(sourcesJson),
                readDecision(decisionJson),
                model,
                createdAt
        );
    }

    private static String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 메모리 이벤트 JSON 저장에 실패했습니다.", e);
        }
    }

    private static List<RagAnswerSource> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, SOURCE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static DecisionResult readDecision(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, DecisionResult.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
