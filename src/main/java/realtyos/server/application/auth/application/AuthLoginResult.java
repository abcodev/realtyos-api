package realtyos.server.application.auth.application;

public record AuthLoginResult(
        Long userId,
        String accessToken,
        String refreshToken,
        String nickname,
        String bio,
        String userType
) {
}
