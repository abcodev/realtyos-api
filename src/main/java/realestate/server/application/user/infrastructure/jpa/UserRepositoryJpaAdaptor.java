package realestate.server.application.user.infrastructure.jpa;

import realestate.server.application.auth.domain.Oauth2Provider;
import realestate.server.application.user.domain.User;
import realestate.server.application.user.domain.UserRepository;
import realestate.server.application.user.domain.UserType;
import realestate.server.application.user.infrastructure.jpa.entity.UserJpaEntity;
import realestate.server.application.user.infrastructure.jpa.mapper.UserMapper;
import realestate.server.application.user.infrastructure.jpa.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserRepositoryJpaAdaptor implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByProviderAndProviderIdOrEmail(Oauth2Provider oauth2Provider, String providerId, String email) {
        return userJpaRepository.findByProviderAndIdOrEmail(oauth2Provider, providerId, email)
                .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userJpaRepository.findAll().stream()
                .anyMatch(e -> e.getNickname().equals(nickname));
    }

    @Override
    public Optional<User> findByEmailAndProvider(Oauth2Provider oauth2Provider, String email) {
        return userJpaRepository.findByOauth2ProviderAndEmail(oauth2Provider, email)
                .map(userMapper::toDomain);
    }

    @Override
    public User fetchBy(long userId) {
        return userJpaRepository.findById(userId)
                .map(userMapper::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. userId: " + userId));
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = userMapper.toEntity(user);
        UserJpaEntity saved = userJpaRepository.save(entity);
        return userMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void updatePushEnabled(Long userId, boolean pushEnabled) {
        UserJpaEntity entity = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. userId: " + userId));
        entity.enablePush(pushEnabled);
        userJpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void updateLocationConsentEnabled(Long userId, boolean locationConsentEnabled) {
        User user = userMapper.toDomain(
                userJpaRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. userId: " + userId))
        );
        User updated = user.enableLocationConsent(locationConsentEnabled);
        userJpaRepository.save(userMapper.toEntity(updated));
    }

    @Override
    @Transactional
    public void updateBriefingPushTime(Long userId, String time) {
        UserJpaEntity entity = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. userId: " + userId));
        entity.updateBriefingPushTime(time);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        userJpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void updateUserType(Long userId, UserType userType) {
        UserJpaEntity entity = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. userId: " + userId));
        entity.updateUserType(userType);
    }
}
