package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonObject;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.model.security.user.JwtTokenModel;
import okhttp3.FormBody;
import okhttp3.Request;

import java.time.Instant;

public class AuthorizationConnection {
    private static AuthorizationConnection instance;
    private JwtTokenModel jwtToken;

    public AuthorizationConnection() {
        loadToken();
    }

    public static synchronized AuthorizationConnection getInstance() {
        if (instance == null) {
            instance = new AuthorizationConnection();
        }

        return instance;
    }

    public synchronized String getApiToken() {
        if (jwtToken.validUntil().isBefore(Instant.now())) {
            loadToken();
        }

        return jwtToken.token();
    }

    public synchronized void loadToken() {
        String url = ConfigProperty.AUTH_LOGIN_URL.getValue();

        String clientId = ConfigProperty.AUTH_CLIENT_ID.getValue();
        String clientSecret = ConfigProperty.AUTH_CLIENT_SECRET.getValue();

        FormBody requestBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        //TODO maybe write own request, since in case of errors this could leak the credentials
        String resultString = DungeonHubConnection.getInstance().executeRequest(request).orElseThrow();
        JsonObject result = DungeonHubService.getInstance().getGson().fromJson(resultString, JsonObject.class);

        String token = result.getAsJsonPrimitive("access_token").getAsString();
        int expiresIn = result.getAsJsonPrimitive("expires_in").getAsInt();
        Instant validUntil = Instant.now().plusSeconds(expiresIn);

        jwtToken = new JwtTokenModel(token, validUntil);
    }
}