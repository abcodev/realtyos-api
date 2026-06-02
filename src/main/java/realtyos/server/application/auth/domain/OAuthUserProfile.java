package realtyos.server.application.auth.domain;

public record OAuthUserProfile(
        String providerId,
        String email,
        String name
) {
}
