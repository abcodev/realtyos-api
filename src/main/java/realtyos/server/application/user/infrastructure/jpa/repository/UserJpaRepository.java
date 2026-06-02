package realtyos.server.application.user.infrastructure.jpa.repository;

import realtyos.server.application.auth.domain.Oauth2Provider;
import realtyos.server.application.user.infrastructure.jpa.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findById(Long userId);

    Optional<UserJpaEntity> findByOauth2ProviderAndProviderId(Oauth2Provider oauth2Provider, String providerId);

    List<UserJpaEntity> findByPushEnabledAndBriefingPushTime(String pushEnabled, String briefingPushTime);
}
