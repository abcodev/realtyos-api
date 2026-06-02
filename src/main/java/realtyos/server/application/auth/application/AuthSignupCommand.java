package realtyos.server.application.auth.application;

public record AuthSignupCommand(
        String accessToken,
        String nickname,
        String authorizationCode,
        String idToken,
        String bio,
        String pushEnabled
) {
}
