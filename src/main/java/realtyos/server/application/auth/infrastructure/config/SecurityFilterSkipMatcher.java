package realtyos.server.application.auth.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityFilterSkipMatcher implements RequestMatcher {

    private static final RequestMatcher DELEGATE = new OrRequestMatcher(List.of(
            PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, "/**"),
            PathPatternRequestMatcher.pathPattern("/api/v1/auth/login/**"),
            PathPatternRequestMatcher.pathPattern("/api/v1/auth/signup/**"),
            PathPatternRequestMatcher.pathPattern("/api/v1/auth/reissue"),
            PathPatternRequestMatcher.pathPattern("/api/v1/auth/token/exchange"),
            PathPatternRequestMatcher.pathPattern("/oauth2/**"),
            PathPatternRequestMatcher.pathPattern("/login/**"),
            PathPatternRequestMatcher.pathPattern("/actuator/health"),
            PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
            PathPatternRequestMatcher.pathPattern("/v3/api-docs/**")
    ));

    @Override
    public boolean matches(HttpServletRequest request) {
        return DELEGATE.matches(request);
    }
}
