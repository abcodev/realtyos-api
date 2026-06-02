package realtyos.server.application.auth.domain;

public interface OAuthUserClient {

    Oauth2Provider provider();

    OAuthUserProfile getUserInfo(String accessToken, String idToken, String authorizationCode, String redirectUri);
}
