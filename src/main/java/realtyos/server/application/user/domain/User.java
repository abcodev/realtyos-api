package realtyos.server.application.user.domain;

import realtyos.server.application.auth.domain.Oauth2Provider;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record User(
        Long id,
        String providerId,
        String email,
        String name,
        String nickname,
        Oauth2Provider oauth2Provider,
        String bio,

        UserType userType,
        UserLevel userLevel,

        String pushEnabled,
        String briefingPushTime,
        boolean locationConsentEnabled,

        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static User createOAuthMember(
            String providerId,
            String email,
            String name,
            String nickname,
            String bio,
            Oauth2Provider provider) {
        return User.builder()
                .providerId(providerId)
                .email(email)
                .name(name)
                .nickname(nickname)
                .oauth2Provider(provider)
                .bio(bio)
                .build();
    }

    public User enablePush(boolean enabled) {
        return new User(
                this.id,
                this.providerId,
                this.email,
                this.name,
                this.nickname,
                this.oauth2Provider,
                this.bio,
                this.userType,
                this.userLevel,
                enabled ? "Y" : "N",
                this.briefingPushTime,
                this.locationConsentEnabled,
                this.lastLoginAt,
                this.createdAt,
                LocalDateTime.now()
        );
    }

    public User enableLocationConsent(boolean enabled) {
        return new User(
                this.id,
                this.providerId,
                this.email,
                this.name,
                this.nickname,
                this.oauth2Provider,
                this.bio,
                this.userType,
                this.userLevel,
                this.pushEnabled,
                this.briefingPushTime,
                enabled,
                this.lastLoginAt,
                this.createdAt,
                LocalDateTime.now()
        );
    }

    public User updateBriefingPushTime(String time) {
        return new User(
                this.id,
                this.providerId,
                this.email,
                this.name,
                this.nickname,
                this.oauth2Provider,
                this.bio,
                this.userType,
                this.userLevel,
                this.pushEnabled,
                time,
                this.locationConsentEnabled,
                this.lastLoginAt,
                this.createdAt,
                LocalDateTime.now()
        );
    }

    @Override
    public String nickname() {
        return nickname;
    }

}
