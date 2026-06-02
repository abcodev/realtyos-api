package realtyos.server.application.auth.interfaces.security;

import realtyos.server.application.auth.domain.Oauth2Provider;
import realtyos.server.application.auth.interfaces.dto.OAuthUserInfo;

import java.util.Map;

public final class OAuthUserInfoFactory {

    private OAuthUserInfoFactory() {
    }

    public static OAuthUserInfo create(Oauth2Provider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case KAKAO -> kakaoUserInfo(attributes);
            case GOOGLE -> googleUserInfo(attributes);
        };
    }

    @SuppressWarnings("unchecked")
    private static OAuthUserInfo kakaoUserInfo(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = kakaoAccount == null ? null : (Map<String, Object>) kakaoAccount.get("profile");
        return new OAuthUserInfo(
                stringValue(attributes.get("id")),
                kakaoAccount == null ? null : stringValue(kakaoAccount.get("email")),
                profile == null ? null : stringValue(profile.get("nickname"))
        );
    }

    private static OAuthUserInfo googleUserInfo(Map<String, Object> attributes) {
        return new OAuthUserInfo(
                stringValue(attributes.get("sub")),
                stringValue(attributes.get("email")),
                stringValue(attributes.get("name"))
        );
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
