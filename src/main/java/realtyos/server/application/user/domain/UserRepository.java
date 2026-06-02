package realtyos.server.application.user.domain;

import java.util.Optional;
import realtyos.server.application.auth.domain.Oauth2Provider;

public interface UserRepository {

    Optional<User> findById(Long id);

    User fetchBy(long userId);

    Optional<User> findByProviderAndProviderId(Oauth2Provider oauth2Provider, String providerId);

    User save(User user);

    void updatePushEnabled(Long userId, boolean pushEnabled);

    void updateLocationConsentEnabled(Long userId, boolean locationConsentEnabled);

    void updateBriefingPushTime(Long userId, String time);

    void updateUserType(Long userId, UserType userType);

    void deleteById(Long id);
}
