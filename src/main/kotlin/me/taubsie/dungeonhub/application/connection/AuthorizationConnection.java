package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonObject;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.model.JwtTokenModel;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

public class AuthorizationConnection {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationConnection.class);

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

        Optional<String> responseBody;

        try (Response response = DungeonHubConnection.getInstance().getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                responseBody = Optional.ofNullable(response.body()).map(body -> {
                    try {
                        return body.bytes();
                    }
                    catch (IOException ioException) {
                        return null;
                    }
                }).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
            } else {
                responseBody = Optional.empty();
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
            responseBody = Optional.empty();
        }






        String resultString = responseBody.orElseThrow();
        JsonObject result = DungeonHubService.getInstance().getGson().fromJson(resultString, JsonObject.class);

        String token = result.getAsJsonPrimitive("access_token").getAsString();
        int expiresIn = result.getAsJsonPrimitive("expires_in").getAsInt();
        Instant validUntil = Instant.now().plusSeconds(expiresIn);

        jwtToken = new JwtTokenModel(token, validUntil);
    }
}