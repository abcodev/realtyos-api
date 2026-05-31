package realtyos.server.application.rag.infrastructure.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realtyos.server.application.rag.infrastructure.jpa.entity.UserAiMemoryJpaEntity;

import java.util.Optional;

public interface UserAiMemoryJpaRepository extends JpaRepository<UserAiMemoryJpaEntity, Long> {

    Optional<UserAiMemoryJpaEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
