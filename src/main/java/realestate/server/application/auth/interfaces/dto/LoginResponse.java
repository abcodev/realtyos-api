package realestate.server.application.auth.interfaces.dto;

public record LoginResponse(
        Long userId,
        String accessToken,
        String refreshToken,
        String nickname,
        String bio,
        String userType) {
}
