package realestate.server.application.user.infrastructure.jpa.entity;

import realestate.server.application.auth.domain.Oauth2Provider;
import realestate.server.application.common.entity.BaseEntity;
import realestate.server.application.user.domain.UserLevel;
import realestate.server.application.user.domain.UserType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class UserJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    private UserLevel userLevel;

    @Column(length = 1000)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth2_provider", nullable = false)
    private Oauth2Provider oauth2Provider;

    @Column(name = "push_enabled", length = 1)
    @Builder.Default
    private String pushEnabled = "N";

    /** 브리핑 푸시 수신 시간 (15분 단위, "HH:mm" 형태. 예: "08:00", "16:15") */
    @Column(name = "briefing_push_time", length = 5)
    @Builder.Default
    private String briefingPushTime = "08:00";

    private boolean locationConsentEnabled;

    private LocalDateTime lastLoginAt;

    public void updateBriefingPushTime(String time) {
        this.briefingPushTime = time;
    }

    public void enablePush(boolean enabled) {
        this.pushEnabled = enabled ? "Y" : "N";
    }

    public void updateUserType(UserType userType) {
        this.userType = userType;
    }
}
