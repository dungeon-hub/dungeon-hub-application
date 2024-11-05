package me.taubsie.dungeonhub.application.connection;

import lombok.Getter;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import okhttp3.*;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Getter
public class DungeonHubConnection {
    private static final Logger logger = LoggerFactory.getLogger(DungeonHubConnection.class);
    private static final String API_PREFIX = "api/v1/";
    private static final String AUTHORIZATION = "Authorization";

    private static DungeonHubConnection instance;

    private final OkHttpClient httpClient;

    private DungeonHubConnection() {
        httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    public static synchronized DungeonHubConnection getInstance() {
        if (instance == null) {
            instance = new DungeonHubConnection();
        }

        return instance;
    }

    public <T> Optional<T> executeRequest(Request request, MappingFunction<String, T> function) {
        return executeRequest(request).map(s -> {
            try {
                return function.apply(s);
            }
            catch (IOException e) {
                return null;
            }
        });
    }

    public Optional<byte[]> executeRawRequest(Request request) {
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Executed request to '{}' successfully.", request.url());

                return Optional.ofNullable(response.body()).map(responseBody -> {
                    try {
                        return responseBody.bytes();
                    }
                    catch (IOException ioException) {
                        logger.error(null, ioException);
                        return null;
                    }
                });
            } else if (response.code() == 404) {
                logger.debug("Executed request to '{}' returned a 404.", request.url());

                return Optional.empty();
            } else {
                String body = getBody(request);

                logger.error("Request to '{}' wasn't successful. Body:\n{}\nResponse: {}\n{}",
                        request.url(),
                        body,
                        response.code(),
                        response.body() != null ? response.body().string() : null);
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }
        return Optional.empty();
    }

    public Optional<String> executeRequest(Request request) {
        return executeRawRequest(request).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public String getBody(Request request) {
        Request newRequest = request.newBuilder().build();

        if (newRequest.body() == null) {
            return null;
        }

        try (Buffer buffer = new Buffer()) {
            newRequest.body().writeTo(buffer);
            return buffer.readUtf8();
        }
        catch (IOException | NullPointerException exception) {
            return null;
        }
    }

    public Request.Builder getApiRequest(String uri) {
        return getApiRequest(getApiUrl(uri).build());
    }

    public Request.Builder getApiRequest(HttpUrl httpUrl) {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        return new Request.Builder()
                .url(httpUrl)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader(AUTHORIZATION, "Bearer " + AuthorizationConnection.getInstance().getApiToken());
    }

    public HttpUrl.Builder getApiUrl(String uri) {
        return HttpUrl.get(ConfigProperty.API_URL + API_PREFIX + uri)
                .newBuilder();
    }
}