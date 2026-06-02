package realtyos.server.application.auth.interfaces.dto;

import realtyos.server.application.auth.domain.AuthToken;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {

    public static TokenResponse from(AuthToken token) {
        return new TokenResponse(token.accessToken(), token.refreshToken());
    }
}
