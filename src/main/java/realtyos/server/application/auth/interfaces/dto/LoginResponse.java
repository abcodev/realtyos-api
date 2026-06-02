package realtyos.server.application.auth.interfaces.dto;

import realtyos.server.application.auth.application.AuthLoginResult;

public record LoginResponse(
        Long userId,
        String accessToken,
        String refreshToken,
        String nickname,
        String bio,
        String userType
) {

    public static LoginResponse from(AuthLoginResult result) {
        return new LoginResponse(
                result.userId(),
                result.accessToken(),
                result.refreshToken(),
                result.nickname(),
                result.bio(),
                result.userType()
        );
    }
}
