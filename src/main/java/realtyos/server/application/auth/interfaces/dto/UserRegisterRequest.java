package realtyos.server.application.auth.interfaces.dto;

public record UserRegisterRequest(
        String accessToken,
        String nickname,
        String authorizationCode,
        String idToken,
        String bio,
        String pushEnabled) {
}
