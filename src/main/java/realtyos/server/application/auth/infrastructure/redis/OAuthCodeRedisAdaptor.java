package realtyos.server.application.auth.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import realtyos.server.application.auth.domain.OAuthCodeRepository;
import realtyos.server.application.auth.interfaces.dto.TokenResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OAuthCodeRedisAdaptor implements OAuthCodeRepository {

    private static final String KEY_PREFIX = "auth:oauth-code:";
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final String DELIMITER = "||";

    private final StringRedisTemplate redisTemplate;

    @Override
    public String save(TokenResponse token) {
        String code = UUID.randomUUID().toString().replace("-", "");
        String value = token.accessToken() + DELIMITER + token.refreshToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, value, TTL);
        return code;
    }

    @Override
    public Optional<TokenResponse> exchange(String code) {
        String key = KEY_PREFIX + code;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        redisTemplate.delete(key);
        String[] parts = value.split("\\|\\|", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        return Optional.of(new TokenResponse(parts[0], parts[1]));
    }
}
