package me.taubsie.dungeonhub.application.connection;

import lombok.Getter;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.exceptions.NotFoundException;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.StrikeData;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import okhttp3.*;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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

    public <T> Optional<T> executeRequest(Request request, Function<String, T> function) {
        return executeRequest(request).map(function);
    }

    public Optional<String> executeRequest(Request request) {
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Executed request to '{}' successfully.", request.url());

                return Optional.ofNullable(response.body()).map(responseBody -> {
                    try {
                        return responseBody.string();
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

                System.out.println(AuthorizationConnection.getInstance().getApiToken());
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }
        return Optional.empty();
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

    public StrikeData loadStrikeDataFromId(long serverId, long id) throws NotFoundException {
        Request request = getApiRequest(getApiUrl("strike/" + serverId)
                .addQueryParameter("id", String.valueOf(id)).build())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new NotFoundException();
            } else if (response.isSuccessful()) {
                if (response.body() != null) {
                    return DungeonHubService.getInstance().getGson().fromJson(response.body().string(),
                            StrikeData.class);
                }
            } else {
                logger.error("Error when trying to load strike by id.");
            }
        }
        catch (IOException ioException) {
            logger.error("Error when trying to load strike by id.", ioException);
        }

        throw new NotFoundException();
    }

    public List<StrikeData> loadValidStrikeData(long serverId, long userId) {
        Request request = getApiRequest(getApiUrl("strike/" + serverId)
                .addQueryParameter("user", String.valueOf(userId)).build())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return DungeonHubService.getInstance().getGson().fromJson(response.body().string(),
                            DungeonHubService.getInstance().getStrikeDataListType());
                }
            } else {
                logger.error("Error when trying to load valid strikes.");
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }

        return new ArrayList<>();
    }

    public List<StrikeData> loadAllStrikeData(long serverId, long userId) {
        Request request = getApiRequest(getApiUrl("strike/" + serverId + "/all")
                .addQueryParameter("user", String.valueOf(userId)).build())
                .get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return DungeonHubService.getInstance().getGson().fromJson(response.body().string(),
                            DungeonHubService.getInstance().getStrikeDataListType());
                }
            } else {
                logger.error("Error when trying to load all strike data of user.");
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }

        return new ArrayList<>();
    }

    public StrikeData insertStrikeData(StrikeData strikeData) {
        RequestBody requestBody = new FormBody.Builder()
                .add("strikeData", DungeonHubService.getInstance().getGson().toJson(strikeData))
                .build();

        Request request = getApiRequest("strike")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return DungeonHubService.getInstance().getGson().fromJson(response.body().string(),
                            StrikeData.class);
                }
            } else {
                logger.error("Error when trying to insert strike.");
            }
        }
        catch (IOException ioException) {
            logger.error("Error when trying to insert strike.", ioException);
        }

        return strikeData;
    }

    public void removeStrike(long serverId, long id) {
        //TODO implement
    }

    //TODO maybe extra endpoint with count() in database
    public int getMaxAllStrikePage(long serverId, long userId) {
        int entries = DungeonHubConnection.getInstance()
                .loadAllStrikeData(serverId, userId)
                .size();

        return (int) Math.ceil(entries / 10.0);
    }

    //TODO maybe extra endpoint with count() in database
    public int getMaxValidStrikePage(long serverId, long userId) {
        int entries = DungeonHubConnection.getInstance()
                .loadValidStrikeData(serverId, userId)
                .size();

        return (int) Math.ceil(entries / 10.0);
    }

    public Optional<CarryTierModel> removeCarryTier(CarryTierModel carryTier) {
        //TODO implement
        return Optional.empty();
    }
}