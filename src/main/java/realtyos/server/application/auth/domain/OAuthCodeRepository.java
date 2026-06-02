package realtyos.server.application.auth.domain;

import realtyos.server.application.auth.interfaces.dto.TokenResponse;

import java.util.Optional;

public interface OAuthCodeRepository {
    String save(TokenResponse token);
    Optional<TokenResponse> exchange(String code);
}
