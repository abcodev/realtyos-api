package realtyos.server.application.auth.application;

public record AuthLoginCommand(
        String accessToken,
        String authorizationCode,
        String idToken,
        String redirectUri,
        String pushEnabled,
        String nickname
) {
}
