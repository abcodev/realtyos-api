package realtyos.server.application.user.infrastructure.jpa.mapper;

import org.springframework.stereotype.Component;
import realtyos.server.application.user.domain.User;
import realtyos.server.application.user.infrastructure.jpa.entity.UserJpaEntity;

@Component
public class UserMapper {

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.builder()
                .id(entity.getId())
                .providerId(entity.getProviderId())
                .email(entity.getEmail())
                .name(entity.getName())
                .nickname(entity.getNickname())
                .oauth2Provider(entity.getOauth2Provider())
                .bio(entity.getBio())
                .userType(entity.getUserType())
                .userLevel(entity.getUserLevel())
                .pushEnabled(entity.getPushEnabled())
                .briefingPushTime(entity.getBriefingPushTime())
                .locationConsentEnabled(entity.isLocationConsentEnabled())
                .lastLoginAt(entity.getLastLoginAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public UserJpaEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        return UserJpaEntity.builder()
                .id(domain.id())
                .providerId(domain.providerId())
                .email(domain.email())
                .name(domain.name())
                .nickname(domain.nickname())
                .oauth2Provider(domain.oauth2Provider())
                .bio(domain.bio())
                .userType(domain.userType())
                .userLevel(domain.userLevel())
                .pushEnabled(domain.pushEnabled())
                .briefingPushTime(domain.briefingPushTime())
                .locationConsentEnabled(domain.locationConsentEnabled())
                .lastLoginAt(domain.lastLoginAt())
                .build();
    }
}
