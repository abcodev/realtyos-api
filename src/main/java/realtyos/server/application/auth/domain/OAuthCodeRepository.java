package realtyos.server.application.auth.domain;

import java.util.Optional;

public interface OAuthCodeRepository {
    String save(AuthToken token);
    Optional<AuthToken> exchange(String code);
}
