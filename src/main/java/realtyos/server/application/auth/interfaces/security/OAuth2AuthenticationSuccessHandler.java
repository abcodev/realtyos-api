package realtyos.server.application.auth.interfaces.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import realtyos.server.application.auth.application.AuthLoginResult;
import realtyos.server.application.auth.domain.AuthToken;
import realtyos.server.application.auth.domain.OAuthCodeRepository;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthCodeRepository oAuthCodeRepository;

    @Value("${auth.frontend-redirect-url}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        AuthLoginResult loginResult = principal.getLoginResult();
        String code = oAuthCodeRepository.save(new AuthToken(
                loginResult.accessToken(),
                loginResult.refreshToken()
        ));

        String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectUrl)
                .queryParam("code", code)
                .build()
                .toUriString();
        response.sendRedirect(targetUrl);
    }
}
