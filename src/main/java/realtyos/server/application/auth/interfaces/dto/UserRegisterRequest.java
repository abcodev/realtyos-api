package realtyos.server.application.auth.interfaces.dto;

import realtyos.server.application.auth.application.AuthSignupCommand;

public record UserRegisterRequest(
        String accessToken,
        String nickname,
        String authorizationCode,
        String idToken,
        String bio,
        String pushEnabled
) {

    public AuthSignupCommand toCommand() {
        return new AuthSignupCommand(accessToken, nickname, authorizationCode, idToken, bio, pushEnabled);
    }
}
