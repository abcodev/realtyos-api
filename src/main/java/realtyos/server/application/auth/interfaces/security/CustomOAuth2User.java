package realtyos.server.application.auth.interfaces.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import realtyos.server.application.auth.interfaces.dto.LoginResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OidcUser {

    private final LoginResponse loginResponse;
    private final Collection<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final OidcIdToken idToken;
    private final OidcUserInfo oidcUserInfo;

    public CustomOAuth2User(LoginResponse loginResponse, Map<String, Object> attributes) {
        this(loginResponse, attributes, null, null);
    }

    public CustomOAuth2User(
            LoginResponse loginResponse,
            Map<String, Object> attributes,
            OidcIdToken idToken,
            OidcUserInfo oidcUserInfo
    ) {
        this.loginResponse = loginResponse;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + loginResponse.userType()));
        this.attributes = attributes;
        this.idToken = idToken;
        this.oidcUserInfo = oidcUserInfo;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return String.valueOf(loginResponse.userId());
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUserInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    public LoginResponse getLoginResponse() {
        return loginResponse;
    }
}
