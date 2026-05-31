package realtyos.server.application.rag.infrastructure.jpa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import realtyos.server.application.rag.infrastructure.jpa.entity.UserAiMemoryEventJpaEntity;

import java.util.List;

public interface UserAiMemoryEventJpaRepository extends JpaRepository<UserAiMemoryEventJpaEntity, Long> {

    List<UserAiMemoryEventJpaEntity> findByUserIdAndRegionIsNotNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserAiMemoryEventJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);
}
