package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.Getter;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.exceptions.NotFoundException;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.OldCarryRole;
import me.taubsie.dungeonhub.common.StrikeData;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.security.user.UserLoginModel;
import me.taubsie.dungeonhub.common.model.security.user.UserLoginVerificationModel;
import me.taubsie.dungeonhub.common.model.security.user.UserTokenRefreshModel;
import okhttp3.*;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@Getter
public class DungeonHubConnection {
    private static final Logger logger = LoggerFactory.getLogger(DungeonHubConnection.class);
    private static final String API_PREFIX = "api/v1/";
    private static final String AUTHORIZATION = "Authorization";
    private static final long TOKEN_REFRESH_TIME = 1000L * 60 * 55;
    private static final long TOKEN_REFRESH_BEFORE_INVALID = (1000L * 60 * 60) - TOKEN_REFRESH_TIME;

    private static DungeonHubConnection instance;

    private final OkHttpClient httpClient;

    @Getter(AccessLevel.NONE)
    private UserLoginVerificationModel loginVerification;

    private DungeonHubConnection() {
        httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        this.loginVerification = loadToken();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshToken();
            }
        }, new Time(System.currentTimeMillis() + TOKEN_REFRESH_TIME), TOKEN_REFRESH_TIME);
    }

    public static synchronized DungeonHubConnection getInstance() {
        if (instance == null) {
            instance = new DungeonHubConnection();
        }

        return instance;
    }

    public synchronized String getApiToken() {
        if (loginVerification.jwtToken().validUntil()
                .isBefore(Instant.now().minus(Math.round(TOKEN_REFRESH_BEFORE_INVALID / (1000 * 60 * 2F)),
                        ChronoUnit.MINUTES))) {
            refreshToken();
        }

        return loginVerification.jwtToken().token();
    }

    public synchronized UserLoginVerificationModel loadToken() {
        String username = ConfigProperty.API_USER.getValue();
        String password = ConfigProperty.API_PASSWORD.getValue();

        UserLoginModel userLoginModel = new UserLoginModel(username, password);

        RequestBody requestBody = RequestBody.create(
                userLoginModel.toJson(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + API_PREFIX + "user/login")
                .post(requestBody)
                .build();

        UserLoginVerificationModel userLoginVerificationModel = executeRequest(request,
                UserLoginVerificationModel::fromJson).orElseThrow();

        return userLoginVerificationModel;
    }

    public synchronized void refreshToken() {
        if (loginVerification == null || loginVerification.refreshToken().validUntil()
                .isBefore(Instant.now().minus(TOKEN_REFRESH_BEFORE_INVALID / (1000 * 60), ChronoUnit.MINUTES))) {
            this.loginVerification = loadToken();
            return;
        }

        UserTokenRefreshModel userTokenRefreshModel =
                new UserTokenRefreshModel(loginVerification.refreshToken().token());

        RequestBody requestBody = RequestBody.create(
                userTokenRefreshModel.toJson(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + API_PREFIX + "user/refresh")
                .post(requestBody)
                .build();

        this.loginVerification = executeRequest(request, UserLoginVerificationModel::fromJson)
                .orElseGet(this::loadToken);
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

    public String[] isFlagged(String user, boolean discord) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.SAFETY_API_URL + "user")
                .newBuilder()
                .addQueryParameter("user", user)
                .addQueryParameter("type", discord ? "discord" : "uuid")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader(AUTHORIZATION, ConfigProperty.SAFETY_API_KEY.getValue())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                return new String[0];
            }

            String body = responseBody.string();
            JsonObject responseObject = JsonParser.parseString(body).getAsJsonObject();
            responseObject = responseObject.getAsJsonObject("data");

            if (responseObject.has("scammer")) {
                return new String[]{
                        "Scammer",
                        responseObject
                                .getAsJsonObject("scammer")
                                .getAsJsonPrimitive("reason")
                                .getAsString()
                };
            } else if (responseObject.has("ratter")) {
                return new String[]{
                        "Ratter",
                        responseObject
                                .getAsJsonObject("ratter")
                                .getAsJsonPrimitive("reason")
                                .getAsString()
                };
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }

        return new String[0];
    }

    public Request.Builder getApiRequest(String uri) {
        return getApiRequest(getApiUrl(uri).build());
    }

    public Request.Builder getApiRequest(HttpUrl httpUrl) {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        return new Request.Builder()
                .url(httpUrl)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader(AUTHORIZATION, "Bearer " + getApiToken());
    }

    public HttpUrl.Builder getApiUrl(String uri) {
        return HttpUrl.get(ConfigProperty.API_URL + API_PREFIX + uri)
                .newBuilder();
    }

    public void addMultipleRoles(Map<Long, List<OldCarryRole>> roleList) {
        RequestBody requestBody = new FormBody.Builder()
                .add("roles", DungeonHubService.getInstance().getGson().toJson(roleList))
                .build();

        Request request = getApiRequest("roles")
                .put(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Error when trying to add roles.");
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }
    }

    public void addRoles(long id, List<OldCarryRole> roles) {
        RequestBody requestBody = new FormBody.Builder()
                .add("id", String.valueOf(id))
                .add("roles", DungeonHubService.getInstance().getGson().toJson(roles))
                .build();

        Request request = getApiRequest("role")
                .put(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException ioException) {
                logger.error(null, ioException);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    logger.error("Error when trying to add roles for user {}.", id);
                }
            }
        });
    }

    public Map<Long, Long> getPurgeableUsers(long amount, long serverId, String type) {
        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(serverId).getByIdentifier(type);

        if (carryType.isEmpty()) {
            logger.error("Error when trying to load carry type {} for purge!", type);
            return new HashMap<>();
        }

        Request request = getApiRequest("purge/" + carryType.get().getId() + "/" + amount)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return DungeonHubService.getInstance().getGson().fromJson(response.body().string(),
                            DungeonHubService.getInstance().getLongLongMapType());
                }
            } else {
                logger.error("Error when trying to load purgable users.");
            }
        }
        catch (IOException ioException) {
            logger.error(null, ioException);
        }

        return new HashMap<>();
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