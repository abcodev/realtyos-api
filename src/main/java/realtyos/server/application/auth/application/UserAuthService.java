package realtyos.server.application.auth.application;

import realtyos.server.application.auth.domain.Oauth2Provider;
import realtyos.server.application.auth.domain.LoginHistory;
import realtyos.server.application.auth.domain.LoginHistoryRepository;
import realtyos.server.application.auth.domain.RefreshTokenStore;
import realtyos.server.application.auth.infrastructure.oauth.GoogleOAuthClient;
import realtyos.server.application.auth.infrastructure.oauth.KakaoOAuthClient;
import realtyos.server.application.auth.interfaces.dto.*;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final UserRepository userRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;
    private final TokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginHistoryRepository loginHistoryRepository;

    private static final long REFRESH_TOKEN_TTL_SECONDS = 60L * 60 * 24 * 14; // 14일

    private OAuthUserInfo resolveOAuthUser(
            Oauth2Provider oauth2Provider, String accessToken, String idToken, String authorizationCode, String redirectUri) {

        return switch (oauth2Provider) {
            case KAKAO -> {
                if (StringUtils.hasText(accessToken)) {
                    yield kakaoOAuthClient.getUserInfo(accessToken);
                }
                if (StringUtils.hasText(authorizationCode)) {
                    yield kakaoOAuthClient.getKakaoUserInfoByCode(authorizationCode, redirectUri);
                }
                throw new IllegalArgumentException("KAKAO requires accessToken or authorizationCode");
            }
            case GOOGLE -> googleOAuthClient.getUserInfo(idToken);
        };
    }

    public Optional<LoginResponse> login(LoginRequest request, Oauth2Provider oauth2Provider, String clientIp) {
        OAuthUserInfo userInfo =
                resolveOAuthUser(oauth2Provider, request.accessToken(), request.idToken(), request.authorizationCode(), request.redirectUri());

        User user = userRepository
                .findByProviderAndProviderIdOrEmail(oauth2Provider, userInfo.providerId(), userInfo.email())
                .orElseGet(() -> registerNewUser(userInfo, oauth2Provider, request.pushEnabled(), request.nickname()));

        if (StringUtils.hasText(request.pushEnabled())) {
            user = user.enablePush("Y".equalsIgnoreCase(request.pushEnabled()));
            user = userRepository.save(user);
        }

        String userTypeName = user.userType() != null ? user.userType().name() : UserType.GENERAL.name();
        AuthToken authToken = tokenProvider.createToken(user.id(), userTypeName);
        refreshTokenStore.save(user.id(), authToken.refreshToken(), REFRESH_TOKEN_TTL_SECONDS);
        recordLoginHistory(user.id(), clientIp);

        return Optional.of(new LoginResponse(
                user.id(),
                authToken.accessToken(),
                authToken.refreshToken(),
                user.nickname(),
                user.bio(),
                userTypeName
                )
        );
    }

    private void recordLoginHistory(Long userId, String clientIp) {
        try {
            loginHistoryRepository.save(LoginHistory.create(userId, clientIp));
        } catch (Exception e) {
            log.warn("로그인 이력 저장 실패 - userId: {}, ip: {}", userId, clientIp, e);
        }
    }

    public LoginResponse signup(UserRegisterRequest request, Oauth2Provider oauth2Provider) {
        OAuthUserInfo userInfo =
                resolveOAuthUser(oauth2Provider, request.accessToken(), request.idToken(), request.authorizationCode(), null);

        String nickname = resolveNickname(userInfo, oauth2Provider, request.nickname());
        String bio = Optional.ofNullable(request.bio()).orElse("");

        User user = loginOrRegister(userInfo, nickname, bio, oauth2Provider, request.pushEnabled());

        String userTypeName = user.userType() != null ? user.userType().name() : UserType.GENERAL.name();
        AuthToken authToken = tokenProvider.createToken(user.id(), userTypeName);
        refreshTokenStore.save(user.id(), authToken.refreshToken(), REFRESH_TOKEN_TTL_SECONDS);

        return new LoginResponse(
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
    public LoginResponse reissue(String refreshToken) {
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

        return new LoginResponse(
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

    public User loginOrRegister(OAuthUserInfo user, String nickname, String bio, Oauth2Provider oauth2Provider, String pushEnabled) {
        return userRepository
                .findByProviderAndProviderIdOrEmail(oauth2Provider, user.providerId(), user.email())
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

    private User registerNewUser(OAuthUserInfo userInfo, Oauth2Provider oauth2Provider, String pushEnabled, String requestNickname) {
        log.info("신규회원 등록 {} user: {} providerId: {}", oauth2Provider.name(), userInfo.email(), userInfo.providerId());

        String nickname = resolveNickname(userInfo, oauth2Provider, requestNickname);

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

    private String resolveNickname(OAuthUserInfo userInfo, Oauth2Provider oauth2Provider, String requestNickname) {
        if (StringUtils.hasText(requestNickname)) {
            return requestNickname;
        }
        if (StringUtils.hasText(userInfo.name())) {
            return userInfo.name();
        }
        return oauth2Provider.name() + "_user";
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
}
