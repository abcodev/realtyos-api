package realtyos.server.application.auth.interfaces.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import realtyos.server.application.auth.application.UserAuthService;
import realtyos.server.application.auth.domain.Oauth2Provider;
import realtyos.server.application.auth.interfaces.dto.LoginResponse;
import realtyos.server.application.auth.interfaces.dto.OAuthUserInfo;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserAuthService userAuthService;
    private final HttpServletRequest request;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Oauth2Provider provider = Oauth2Provider.from(registrationId);
        OAuthUserInfo userInfo = OAuthUserInfoFactory.create(provider, oidcUser.getAttributes());
        LoginResponse loginResponse = userAuthService.loginWithOAuthUser(userInfo, provider, resolveClientIp());

        return new CustomOAuth2User(
                loginResponse,
                oidcUser.getAttributes(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );
    }

    private String resolveClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
