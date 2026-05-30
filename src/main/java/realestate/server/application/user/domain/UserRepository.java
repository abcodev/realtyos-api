package realestate.server.application.user.domain;

import realestate.server.application.auth.domain.Oauth2Provider;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    boolean existsByNickname(String nickname);

    Optional<User> findByEmailAndProvider(Oauth2Provider oauth2Provider, String email);

    Optional<User> findById(Long id);

    User fetchBy(long userId);

    Optional<User> findByProviderAndProviderIdOrEmail(Oauth2Provider oauth2Provider, String providerId, String email);

    User save(User user);

    void updatePushEnabled(Long userId, boolean pushEnabled);

    void updateLocationConsentEnabled(Long userId, boolean locationConsentEnabled);

    void updateBriefingPushTime(Long userId, String time);

    void updateUserType(Long userId, UserType userType);

    void deleteById(Long id);
}
