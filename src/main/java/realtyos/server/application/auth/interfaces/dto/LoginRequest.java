package realtyos.server.application.auth.interfaces.dto;

import realtyos.server.application.auth.application.AuthLoginCommand;

public record LoginRequest(
        String accessToken,
        String authorizationCode,
        String idToken,
        String redirectUri,
        String pushEnabled,
        String nickname
) {

    public AuthLoginCommand toCommand() {
        return new AuthLoginCommand(accessToken, authorizationCode, idToken, redirectUri, pushEnabled, nickname);
    }
}
