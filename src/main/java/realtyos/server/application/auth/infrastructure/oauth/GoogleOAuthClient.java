package realtyos.server.application.auth.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import realtyos.server.application.auth.interfaces.dto.OAuthUserInfo;
import realtyos.server.application.common.exception.AuthExceptionCode;
import realtyos.server.application.common.exception.CustomAuthException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    private final RestClient restClient;

    @Value("${oauth.google.client-id:}")
    private String clientId;

    public OAuthUserInfo getUserInfo(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("GOOGLE requires idToken");
        }

        try {
            GoogleTokenInfo response = restClient.get()
                    .uri(TOKEN_INFO_URL, idToken)
                    .retrieve()
                    .body(GoogleTokenInfo.class);

            if (response == null || !StringUtils.hasText(response.sub())) {
                throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
            }
            if (StringUtils.hasText(clientId) && !clientId.equals(response.aud())) {
                log.warn("Google id_token audience mismatch - expected: {}, actual: {}", clientId, response.aud());
                throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
            }

            return new OAuthUserInfo(response.sub(), response.email(), response.name());
        } catch (RestClientResponseException e) {
            log.error("Failed to verify Google id_token: {}", e.getResponseBodyAsString(), e);
            throw new CustomAuthException(AuthExceptionCode.UN_AUTHORIZATION);
        }
    }

    record GoogleTokenInfo(
            String sub,
            String email,
            String name,
            String aud,
            @JsonProperty("email_verified") String emailVerified
    ) {
    }
}
