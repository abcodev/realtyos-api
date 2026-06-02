package realtyos.server.application.auth.infrastructure.oauth;

import realtyos.server.application.auth.interfaces.dto.OAuthUserInfo;
import realtyos.server.application.common.exception.AuthExceptionCode;
import realtyos.server.application.common.exception.CustomAuthException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    @Value("${oauth.kakao.client-id:${spring.security.oauth2.client.registration.kakao.client-id:}}")
    private String clientId;

    @Value("${oauth.kakao.redirect-uri:http://localhost:18080/login/oauth2/code/kakao}")
    private String configuredRedirectUri;

    private final RestClient restClient;

    /**
     * 인가코드로 카카오 유저 정보를 가져옵니다.
     * (인가코드 → 액세스 토큰 교환 → 유저 프로필 조회)
     */
    public OAuthUserInfo getKakaoUserInfoByCode(String authorizationCode, String redirectUri) {
        String accessToken = getAccessToken(authorizationCode, redirectUri);
        return getUserInfo(accessToken);
    }

    /**
     * 액세스 토큰으로 카카오 유저 정보를 가져옵니다.
     */
    public OAuthUserInfo getUserInfo(String accessToken) {
        try {
            KakaoProfileResponse profileResponse = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoProfileResponse.class);

            if (profileResponse == null) {
                log.error("Kakao Profile Response is null");
                throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
            }

            String email = profileResponse.kakaoAccount() != null ? profileResponse.kakaoAccount().email() : null;
            String nickname = profileResponse.kakaoAccount() != null && profileResponse.kakaoAccount().profile() != null ?
                    profileResponse.kakaoAccount().profile().nickname() : null;
            String providerId = profileResponse.id() != null ? profileResponse.id().toString() : null;

            return new OAuthUserInfo(providerId, email, nickname);
        } catch (RestClientResponseException e) {
            log.error("Failed to get Kakao user profile: {}", e.getResponseBodyAsString(), e);
            throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
        }
    }

    private String getAccessToken(String authorizationCode, String redirectUri) {
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : configuredRedirectUri;
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("redirect_uri", resolvedRedirectUri);
        body.add("code", authorizationCode);

        try {
            KakaoTokenResponse tokenResponse = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                log.error("Kakao Token Response is null");
                throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
            }
            return tokenResponse.accessToken();
        } catch (RestClientResponseException e) {
            log.error("Failed to get Kakao access token: {}", e.getResponseBodyAsString(), e);
            throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
        }
    }

    record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Integer expiresIn,
            @JsonProperty("scope") String scope,
            @JsonProperty("refresh_token_expires_in") Integer refreshTokenExpiresIn) {
    }

    record KakaoProfileResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    record KakaoAccount(
            @JsonProperty("profile") Profile profile,
            @JsonProperty("email") String email) {
    }

    record Profile(
            @JsonProperty("nickname") String nickname) {
    }
}
