package realtyos.server.application.rag.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAiMemoryEventJpaRepository extends JpaRepository<UserAiMemoryEventJpaEntity, Long> {

    List<UserAiMemoryEventJpaEntity> findByUserIdAndRegionIsNotNullOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
