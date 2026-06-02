package realtyos.server.application.auth.interfaces.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import realtyos.server.application.auth.application.AuthLoginResult;
import realtyos.server.application.auth.application.UserAuthService;
import realtyos.server.application.auth.domain.OAuthUserProfile;
import realtyos.server.application.auth.domain.Oauth2Provider;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserAuthService userAuthService;
    private final HttpServletRequest request;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Oauth2Provider provider = Oauth2Provider.from(registrationId);
        OAuthUserProfile userInfo = OAuthUserInfoFactory.create(provider, oAuth2User.getAttributes());
        AuthLoginResult loginResult = userAuthService.loginWithOAuthUser(userInfo, provider, resolveClientIp());

        return new CustomOAuth2User(loginResult, oAuth2User.getAttributes());
    }

    private String resolveClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
