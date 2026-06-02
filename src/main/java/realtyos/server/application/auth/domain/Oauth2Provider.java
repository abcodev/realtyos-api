package realtyos.server.application.auth.domain;

import java.util.Arrays;

public enum Oauth2Provider {
    KAKAO,
    GOOGLE;

    public static Oauth2Provider from(String value) {
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + value));
    }
}
