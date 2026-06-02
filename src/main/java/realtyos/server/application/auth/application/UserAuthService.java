package realtyos.server.application.auth.application;

import realtyos.server.application.auth.domain.Oauth2Provider;
import realtyos.server.application.auth.domain.LoginHistory;
import realtyos.server.application.auth.domain.LoginHistoryRepository;
import realtyos.server.application.auth.domain.OAuthUserClient;
import realtyos.server.application.auth.domain.OAuthUserProfile;
import realtyos.server.application.auth.domain.RefreshTokenStore;
import realtyos.server.application.common.exception.BusinessException;
import realtyos.server.application.common.exception.ErrorCode;
import realtyos.server.application.user.domain.User;
import realtyos.server.application.user.domain.UserHistory;
import realtyos.server.application.user.domain.UserHistoryRepository;
import realtyos.server.application.user.domain.UserLevel;
import realtyos.server.application.user.domain.UserRepository;
import realtyos.server.application.auth.domain.AuthToken;
import realtyos.server.application.auth.domain.TokenProvider;
import realtyos.server.application.user.domain.UserType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final UserRepository userRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final List<OAuthUserClient> oAuthUserClients;
    private final TokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginHistoryRepository loginHistoryRepository;

    private static final long REFRESH_TOKEN_TTL_SECONDS = 60L * 60 * 24 * 14; // 14일

    private OAuthUserProfile resolveOAuthUser(
            Oauth2Provider oauth2Provider, String accessToken, String idToken, String authorizationCode, String redirectUri) {
        OAuthUserClient client = oAuthUserClientsByProvider().get(oauth2Provider);
        if (client == null) {
            throw new IllegalArgumentException("Unsupported OAuth provider: " + oauth2Provider);
        }
        return client.getUserInfo(accessToken, idToken, authorizationCode, redirectUri);
    }

    public Optional<AuthLoginResult> login(AuthLoginCommand command, Oauth2Provider oauth2Provider, String clientIp) {
        OAuthUserProfile userInfo =
                resolveOAuthUser(oauth2Provider, command.accessToken(), command.idToken(), command.authorizationCode(), command.redirectUri());

        return Optional.of(loginWithOAuthUser(userInfo, oauth2Provider, clientIp, command.pushEnabled(), command.nickname()));
    }

    public AuthLoginResult loginWithOAuthUser(OAuthUserProfile userInfo, Oauth2Provider oauth2Provider, String clientIp) {
        return loginWithOAuthUser(userInfo, oauth2Provider, clientIp, null, null);
    }

    private AuthLoginResult loginWithOAuthUser(
            OAuthUserProfile userInfo,
            Oauth2Provider oauth2Provider,
            String clientIp,
            String pushEnabled,
            String requestNickname
    ) {
        User user = userRepository
                .findByProviderAndProviderId(oauth2Provider, userInfo.providerId())
                .orElseGet(() -> registerNewUser(userInfo, oauth2Provider, pushEnabled, requestNickname));

        if (StringUtils.hasText(pushEnabled)) {
            user = user.enablePush("Y".equalsIgnoreCase(pushEnabled));
            user = userRepository.save(user);
        }

        String userTypeName = user.userType() != null ? user.userType().name() : UserType.GENERAL.name();
        AuthToken authToken = tokenProvider.createToken(user.id(), userTypeName);
        refreshTokenStore.save(user.id(), authToken.refreshToken(), REFRESH_TOKEN_TTL_SECONDS);
        recordLoginHistory(user.id(), clientIp);

        return new AuthLoginResult(
                user.id(),
                authToken.accessToken(),
                authToken.refreshToken(),
                user.nickname(),
                user.bio(),
                userTypeName
        );
    }

    private void recordLoginHistory(Long userId, String clientIp) {
        try {
            loginHistoryRepository.save(LoginHistory.create(userId, clientIp));
        } catch (Exception e) {
            log.warn("로그인 이력 저장 실패 - userId: {}, ip: {}", userId, clientIp, e);
        }
    }

    public AuthLoginResult signup(AuthSignupCommand command, Oauth2Provider oauth2Provider) {
        OAuthUserProfile userInfo =
                resolveOAuthUser(oauth2Provider, command.accessToken(), command.idToken(), command.authorizationCode(), null);

        String nickname = resolveNickname(userInfo, command.nickname());
        String bio = Optional.ofNullable(command.bio()).orElse("");

        User user = loginOrRegister(userInfo, nickname, bio, oauth2Provider, command.pushEnabled());

        String userTypeName = user.userType() != null ? user.userType().name() : UserType.GENERAL.name();
        AuthToken authToken = tokenProvider.createToken(user.id(), userTypeName);
        refreshTokenStore.save(user.id(), authToken.refreshToken(), REFRESH_TOKEN_TTL_SECONDS);

        return new AuthLoginResult(
                user.id(),
                authToken.accessToken(),
                authToken.refreshToken(),
                user.nickname(),
                user.bio(),
                userTypeName
                );
    }

    /**
     * Refresh Token으로 Access/Refresh Token 재발급
     */
    @Transactional
    public AuthLoginResult reissue(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }

        Long userId = tokenProvider.parseUserId(refreshToken);

        String storedToken = refreshTokenStore.find(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED));

        if (!storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        String userTypeName = user.userType() != null ? user.userType().name() : UserType.GENERAL.name();
        AuthToken newToken = tokenProvider.createToken(userId, userTypeName);
        refreshTokenStore.save(userId, newToken.refreshToken(), REFRESH_TOKEN_TTL_SECONDS);

        return new AuthLoginResult(
                user.id(),
                newToken.accessToken(),
                newToken.refreshToken(),
                user.nickname(),
                user.bio(),
                userTypeName
        );
    }

    /**
     * 로그아웃 — Refresh Token 무효화
     */
    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
        log.info("로그아웃 완료 - userId: {}", userId);
    }

    public User loginOrRegister(OAuthUserProfile user, String nickname, String bio, Oauth2Provider oauth2Provider, String pushEnabled) {
        return userRepository
                .findByProviderAndProviderId(oauth2Provider, user.providerId())
                .map(existingUser -> {
                    if (StringUtils.hasText(pushEnabled)) {
                        User updatedUser = existingUser.enablePush("Y".equalsIgnoreCase(pushEnabled));
                        return userRepository.save(updatedUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    User newUser = User.createOAuthMember(user.providerId(), user.email(), user.name(), nickname, bio, oauth2Provider);
                    if (StringUtils.hasText(pushEnabled) && "Y".equalsIgnoreCase(pushEnabled)) {
                        newUser = newUser.enablePush(true).updateBriefingPushTime("08:00");
                    }
                    User savedUser = userRepository.save(newUser);
                    userHistoryRepository.save(UserHistory.createNew(savedUser.id(), savedUser.oauth2Provider(), savedUser.providerId(), savedUser.createdAt()));
                    return savedUser;
                });
    }

    private User registerNewUser(OAuthUserProfile userInfo, Oauth2Provider oauth2Provider, String pushEnabled, String requestNickname) {
        log.info("신규회원 등록 {} providerId: {}", oauth2Provider.name(), userInfo.providerId());

        String nickname = resolveNickname(userInfo, requestNickname);
        boolean isPushEnabled = StringUtils.hasText(pushEnabled) && "Y".equalsIgnoreCase(pushEnabled);

        User newUser = User.builder()
                .providerId(userInfo.providerId())
                .oauth2Provider(oauth2Provider)
                .email(userInfo.email())
                .name(userInfo.name())
                .nickname(nickname)
                .userType(UserType.GENERAL)
                .userLevel(UserLevel.BASIC)
                .pushEnabled(isPushEnabled ? "Y" : "N")
                .briefingPushTime(isPushEnabled ? "08:00" : null)
                .locationConsentEnabled(true)
                .lastLoginAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);
        userHistoryRepository.save(UserHistory.createNew(savedUser.id(), savedUser.oauth2Provider(), savedUser.providerId(), savedUser.createdAt()));
        return savedUser;
    }

    private String resolveNickname(OAuthUserProfile userInfo, String requestNickname) {
        if (StringUtils.hasText(requestNickname)) {
            return requestNickname;
        }
        return userInfo.name();
    }

    @Transactional
    public boolean withdraw(long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        userHistoryRepository.findByUserId(userId)
                .ifPresentOrElse(
                        history -> userHistoryRepository.save(history.markDeleted()),
                        () -> userHistoryRepository.save(UserHistory.createNew(userId, user.oauth2Provider(), user.providerId(), user.createdAt()).markDeleted())
                );
        userRepository.deleteById(userId);
        
        refreshTokenStore.delete(userId);
        log.info("회원 탈퇴 처리 완료 - userId: {}", userId);
        return true;
    }

    private Map<Oauth2Provider, OAuthUserClient> oAuthUserClientsByProvider() {
        return oAuthUserClients.stream()
                .collect(Collectors.toMap(OAuthUserClient::provider, Function.identity()));
    }
}
