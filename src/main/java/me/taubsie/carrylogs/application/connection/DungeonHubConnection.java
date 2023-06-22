package me.taubsie.carrylogs.application.connection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.taubsie.carrylogs.application.exceptions.NotFoundException;
import me.taubsie.carrylogs.application.exceptions.PlayerNotFoundException;
import me.taubsie.dungeonhub.common.*;
import me.taubsie.dungeonhub.common.config.ConfigProperty;
import okhttp3.*;
import org.javacord.api.entity.server.Server;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Time;
import java.time.Duration;
import java.util.*;

//TODO maybe split up in different services for each api (internal, hypixel, ...)
public class DungeonHubConnection {
    private static final Logger logger = LoggerFactory.getLogger(DungeonHubConnection.class);

    private static final String API_PREFIX = "api/v1/";

    private static final String AUTHORIZATION = "Authorization";

    private static final int[] requiredXp = {50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700,
            17960, 25340, 35640, 50040, 70040, 97640, 135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640,
            1684640, 2284640, 3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640,
            39559640, 51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640,
            453559640, 569809640};

    private static final long TOKEN_REFRESH_TIME = 1000L * 60 * 55;
    private static DungeonHubConnection instance;

    private final OkHttpClient httpClient;

    @Getter
    private String apiToken;

    private DungeonHubConnection() {
        httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        reloadToken();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                reloadToken();
            }
        }, new Time(System.currentTimeMillis() + TOKEN_REFRESH_TIME), TOKEN_REFRESH_TIME);
    }

    public static DungeonHubConnection getInstance() {
        if (instance == null) {
            instance = new DungeonHubConnection();
        }

        return instance;
    }

    /**
     * Do not call this outside of constructor or the scheduled run.
     */
    private void reloadToken() {
        String username = ConfigProperty.API_USER.getValue();
        String password = ConfigProperty.API_PASSWORD.getValue();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "api/token")
                .get()
                .addHeader(AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Token-request wasn't successful!");
                return;
            }

            apiToken = response.body().string();
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
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
            ioException.printStackTrace();
        }

        return new String[0];
    }

    private Request.Builder getApiRequest(String uri) {
        return getApiRequest(getApiUrl(uri).build());
    }

    private Request.Builder getApiRequest(HttpUrl httpUrl) {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        return new Request.Builder()
                .url(httpUrl)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader(AUTHORIZATION, "Bearer " + apiToken);
    }

    private HttpUrl.Builder getApiUrl(String uri) {
        return HttpUrl.get(ConfigProperty.API_URL + API_PREFIX + uri)
                .newBuilder();
    }

    private RequestBody getRequestBody(Long id) {
        return new FormBody.Builder()
                .add("id", id.toString())
                .build();
    }

    private RequestBody getRequestBody(CarryInformation carryInformation) {
        return new FormBody.Builder()
                .add("carryInformation", carryInformation.toJson())
                .build();
    }

    private RequestBody getRequestBody(Long id, CarryInformation carryInformation) {
        return new FormBody.Builder()
                .add("id", id.toString())
                .add("carryInformation", carryInformation.toJson())
                .build();
    }

    private RequestBody getRequestBody(String identifier, String displayName) {
        return new FormBody.Builder()
                .add("identifier", identifier)
                .add("displayName", displayName)
                .build();
    }

    public void addToLogQueue(Long id, CarryInformation carryInformation) {
        Request request = getApiRequest("log-queue")
                .post(getRequestBody(id, carryInformation))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Added new carry to log-queue.");
            } else {
                logger.error("Adding new carry to log-queue wasn't successful");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void addToApprovingQueue(Long id, CarryInformation carryInformation) {
        Request request = getApiRequest("approving-queue")
                .post(getRequestBody(id, carryInformation))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Added new carry to approving-queue.");
            } else {
                logger.error("Adding new carry to approving-queue wasn't successful.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void removeFromApprovingQueue(Long id) {
        Request request = getApiRequest("approving-queue")
                .delete(getRequestBody(id))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Removed a carry from approving-queue.");
            } else {
                logger.error("Removing a carry from approving-queue wasn't successful.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void removeFromLogQueue(Long id) {
        Request request = getApiRequest("log-queue")
                .delete(getRequestBody(id))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Removed a carry from log-queue.");
            } else {
                logger.error("Removing a carry from log-queue wasn't successful.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public Set<CarryInformation> getFromLogApprovingQueue(Long id) {
        Request request = getApiRequest(getApiUrl("approving-queue")
                .addQueryParameter("id", String.valueOf(id))
                .build())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Loaded carries from approving-queue.");

                if (response.body() == null) {
                    return new HashSet<>();
                }

                return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                        CarryLogService.getInstance().getCarryInformationSetType());
            } else {
                logger.error("Loading carries from approving-queue threw an error: {}",
                        response.body() != null ? response.body().string() : response.code());
            }
            return new HashSet<>();
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
            return new HashSet<>();
        }
    }

    public Set<CarryInformation> getFromLogQueue(Long id) {
        Request request = getApiRequest(getApiUrl("log-queue")
                .addQueryParameter("id", String.valueOf(id))
                .build())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Loaded carries from log-queue.");

                if (response.body() == null) {
                    return new HashSet<>();
                }

                return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                        CarryLogService.getInstance().getCarryInformationSetType());
            } else {
                logger.error("Error while loading logging-queue: {}",
                        response.body() != null ? response.body().string() : response.code());
            }
            return new HashSet<>();
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
            return new HashSet<>();
        }
    }

    public long logCarry(CarryInformation carryInformation) {
        Request request = getApiRequest("log")
                .post(getRequestBody(carryInformation))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.debug("Logged carry successfully.");
                if (response.body() != null) {
                    //TODO nested try-block -> refactor or put into extra method
                    try {
                        return Long.parseLong(response.body().string());
                    }
                    catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }
                }
            } else {
                logger.error("Error when trying to log carry.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return getScoreFromCarrier(carryInformation);
    }

    public long getScoreFromCarrier(CarryInformation carryInformation) {
        return getScore(carryInformation.getServerId(), carryInformation.getCarryType());
    }

    public List<ScoreValue> countScore(long serverId, Long id) {
        Request request = getApiRequest("server/" + serverId + "/carry-score/" + id)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getScoreValueListType());
                }
            } else {
                logger.error("Error when trying to count carries.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return new ArrayList<>();
    }

    public long getScore(Long id, CarryType carryType) {
        Request request =
                getApiRequest("server" + carryType.getServer() + "/carry-score/" + id + "/" + carryType.getId())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return Long.parseLong(response.body().string());
                }
            } else {
                logger.error("Error when trying to get score.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return 0L;
    }

    public Map<Long, Long> getLeaderboardData(@NotNull CarryType carryType, ScoreType scoreType, int page) {
        Request request = getApiRequest(getApiUrl("leaderboard/" + carryType.getId() + "/" + scoreType.getName())
                .addQueryParameter("page", String.valueOf(page))
                .build())
                .get()
                .build();

        Map<Long, Long> result = new HashMap<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    result = CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getLongLongMapType());
                }
            } else {
                logger.error("Error when trying to get leaderboard.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return result;
    }

    public long modifyScore(Long id, CarryType carryType, Long amount) {
        RequestBody requestBody = new FormBody.Builder()
                .add("amount", String.valueOf(amount))
                .build();

        Request request = getApiRequest("carry-score/" + id + "/" + carryType.getId())
                .put(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return Long.parseLong(response.body().string());
                }
            } else {
                logger.error("Error when trying to update score.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return 0L;
    }

    public void addMultipleRoles(Map<Long, List<OldCarryRole>> roleList) {
        RequestBody requestBody = new FormBody.Builder()
                .add("roles", CarryLogService.getInstance().getGson().toJson(roleList))
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
            ioException.printStackTrace();
        }
    }

    public void addRoles(long id, List<OldCarryRole> roles) {
        RequestBody requestBody = new FormBody.Builder()
                .add("id", String.valueOf(id))
                .add("roles", CarryLogService.getInstance().getGson().toJson(roles))
                .build();

        Request request = getApiRequest("role")
                .put(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException ioException) {
                ioException.printStackTrace();
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
        Optional<CarryType> carryType = loadCarryType(serverId, type);

        if(carryType.isEmpty()) {
            logger.error("Error when trying to load carry type {} for purge!", type);
            return new HashMap<>();
        }

        Request request = getApiRequest("purge/" + carryType.get().getId() + "/" + amount)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getLongLongMapType());
                }
            } else {
                logger.error("Error when trying to load purgable users.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return new HashMap<>();
    }

    public UUID fromString(String uuid) {
        //TODO fix?
        //TODO check for uuid format
        return UUID.fromString(String.format("%s-%s-%s-%s-%s", uuid.substring(0, 7), uuid.substring(7, 11),
                uuid.substring(11, 15), uuid.substring(15, 20), uuid.substring(20, 32)));
    }


    //As this requests data from the Mojang API (aka slow), it is recommended to use UUIDs instead of names
    public UUID getUUIDByName(String name) throws PlayerNotFoundException {
        Request request = new Request.Builder()
                .url("https://api.mojang.com/users/profiles/minecraft/" + name)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return fromString(JsonParser.parseString(response.body().string()).getAsJsonObject().get("id").getAsString());
            }
        }
        catch (IOException | NullPointerException exception) {
            exception.printStackTrace();
        }

        throw new PlayerNotFoundException(name);
    }

    //This is a request on the Hypixel API, and therefore unneccessary calls should be avoided
    public int getCataLevelByUUID(UUID uuid) {
        JsonArray profiles = getProfiles(uuid);
        if (profiles == null) return 0;

        //Highest cata xp of all profiles
        double highestXP = 0;

        for(int i = 0; i < profiles.size(); i++) {
            try {
                double thisXP = profiles.get(i).getAsJsonObject()
                        .getAsJsonObject("members")
                        .get(uuid.toString().replace("-", ""))
                        .getAsJsonObject()
                        .getAsJsonObject("dungeons")
                        .getAsJsonObject("dungeon_types")
                        .getAsJsonObject("catacombs")
                        .get("experience")
                        .getAsDouble();
                highestXP = Math.max(highestXP, thisXP);
                // null if profile hasn't entered dungeons
            }
            catch (NullPointerException ignored) {
                //TODO this happens if the profile hasn't entered dungeons. Custom exception?
            }
        }

        return cataXPToLevel(highestXP);
    }

    public JsonArray getProfiles(UUID uuid) {
        Request request = new Request.Builder()
                .url("https://api.hypixel.net/skyblock/profiles?key=" + ConfigProperty.HYPIXEL_API_KEY.getValue() +
                        "&uuid=" + uuid)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Unsuccessful profile request for UUID {}", uuid);
                return null;
            }

            return JsonParser.parseString(response.body().string()).getAsJsonObject().getAsJsonArray("profiles");
        }
        catch (IOException ioException) {
            logger.error("Profile request for UUID threw an error.", ioException);
        }

        return null;
    }

    private int cataXPToLevel(double xp) {
        for(int i = 0; i < requiredXp.length; i++) {
            if (requiredXp[i] > xp) return i;
        }

        // 50 and everything higher is returned as 50
        return 50;
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
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(), StrikeData.class);
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
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getStrikeDataListType());
                }
            } else {
                logger.error("Error when trying to load valid strikes.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
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
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getStrikeDataListType());
                }
            } else {
                logger.error("Error when trying to load all strike data of user.");
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return new ArrayList<>();
    }

    public StrikeData insertStrikeData(StrikeData strikeData) {
        RequestBody requestBody = new FormBody.Builder()
                .add("strikeData", CarryLogService.getInstance().getGson().toJson(strikeData))
                .build();

        Request request = getApiRequest("strike")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(), StrikeData.class);
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

    //TODO rework with new system
    public int getMaxLeaderboardPage(CarryType carryType, ScoreType scoreType) {
        Request request = getApiRequest("leaderboard/" + carryType.getId() + "/pages/" + scoreType.getName())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return Integer.parseInt(response.body().string());
                }
            } else {
                String result = "Error when trying to load the max leaderboard page of type {} {}";
                if (response.body() != null) {
                    result += ":\n{}";
                    String exception = response.body().string();
                    logger.error(result, carryType.getId(), scoreType.getName(), exception);
                } else {
                    result += ".";
                    logger.error(result, carryType.getId(), scoreType.getName());
                }
            }
        }
        catch (NumberFormatException numberFormatException) {
            logger.error("Couldn't parse number from return value (max leaderboard page).", numberFormatException);
        }
        catch (IOException ioException) {
            logger.error("Error when loading the max leaderboard page for type {} {}.", carryType.getId(), scoreType.getName(), ioException);
        }

        return 1;
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

    /**
     * Loads all available carry types from the given server.
     * This represents the types of carry, so for example dungeons, slayer, kuudra, ...
     *
     * @param serverId The server to load this for.
     * @return The list of carry types that were loaded from the database.
     */
    public List<CarryType> loadCarryTypesForServer(long serverId) {
        Request request = getApiRequest("server/" + serverId + "/carry-type")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                        CarryLogService.getInstance().getCarryTypeListType());
            } else {
                String result = "Error while trying to load the carry types for server {}";
                if (response.body() != null) {
                    result += ":\n{}";
                    String exception = response.body().string();
                    logger.error(result, serverId, exception);
                } else {
                    result += ".";
                    logger.error(result, serverId);
                }
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to load carry types for server {}.", serverId, ioException);
        }

        return new ArrayList<>();
    }

    public List<CarryType> loadCarryTypes() {
        Request request = getApiRequest("carry-types")
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                        CarryLogService.getInstance().getCarryTypeListType());
            }
        } catch (IOException ioException) {
            logger.error("Error while trying to load all carry types.", ioException);
        }

        return new ArrayList<>();
    }

    public List<CarryTier> loadCarryTiers() {
        Request request = getApiRequest("carry-tiers")
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                        CarryLogService.getInstance().getCarryTierListType());
            }
        } catch (IOException ioException) {
            logger.error("Error while trying to load all carry tiers.", ioException);
        }

        return new ArrayList<>();
    }

    public List<CarryDifficulty> loadCarryDifficulties() {
        Request request = getApiRequest("carry-difficulties")
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                        CarryLogService.getInstance().getCarryDifficultyListType());
            }
        } catch (IOException ioException) {
            logger.error("Error while trying to load all carry difficulties.", ioException);
        }

        return new ArrayList<>();
    }

    /**
     * Loads all available carry tiers for the given carry type.
     * This represents the tiers of carry, so for example floor 1, master mode floor 1, tier 4, kuudra, ...
     *
     * @param carryType The carry type to load this for.
     * @return The list of carry tiers that were loaded from the database.
     */
    public List<CarryTier> loadCarryTiers(CarryType carryType) {
        //TODO implement properly
        return loadCarryTiers()
                .stream().filter(carryTier -> carryTier.getCarryType().equals(carryType))
                .toList();
    }

    public List<CarryTier> loadCarryTiers(Server server) {
        //TODO implement properly
        return loadCarryTiers()
                .stream().filter(carryTier -> carryTier.getCarryType().getServer() == server.getId())
                .toList();
    }

    public List<CarryDifficulty> loadCarryDifficulties(CarryTier carryTier) {
        //TODO implement properly
        return loadCarryDifficulties()
                .stream().filter(carryDifficulty -> carryDifficulty.getCarryTier().equals(carryTier))
                .toList();
    }

    public Optional<CarryType> loadCarryType(long serverId, String identifier) {
        HttpUrl httpUrl = getApiUrl("server/" + serverId + "/carry-type")
                .addQueryParameter("identifier", identifier)
                .build();

        Request request = getApiRequest(httpUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryType.fromJson(response.body().string()));
            } else if(response.code() == 404) {
                return Optional.empty();
            } else {
                String result = "Error while trying to load carry type {} for server {}";
                if (response.body() != null) {
                    result += ":\n{}";
                    String exception = response.body().string();
                    logger.error(result, identifier, serverId, exception);
                } else {
                    result += ".";
                    logger.error(result, identifier, serverId);
                }
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to load carry type {} for server {}.", identifier, serverId, ioException);
        }

        return Optional.empty();
    }

    public Optional<CarryTier> loadCarryTier(CarryType carryType, String identifier) {
        //TODO implement properly
        return loadCarryTiers()
                .stream().filter(carryTier -> carryTier.getCarryType().equals(carryType))
                .filter(carryTier -> carryTier.getIdentifier().equalsIgnoreCase(identifier))
                .findFirst();
    }

    public Optional<CarryDifficulty> loadCarryDifficulty(CarryTier carryTier, String identifier) {
        //TODO implement properly
        return loadCarryDifficulties()
                .stream().filter(carryDifficulty -> carryDifficulty.getCarryTier().equals(carryTier))
                .filter(carryDifficulty -> carryDifficulty.getIdentifier().equalsIgnoreCase(identifier))
                .findFirst();
    }

    public Optional<CarryType> addNewCarryType(long serverId, String identifier, String displayName) {
        RequestBody requestBody = getRequestBody(identifier, displayName);

        Request request = getApiRequest("server/" + serverId + "/carry-type")
                .post(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryType.fromJson(response.body().string()));
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to add new carry type {} to server {}.", identifier, serverId, ioException);
        }

        return Optional.empty();
    }

    public Optional<CarryType> removeCarryType(CarryType carryType) {
        Request request = getApiRequest("server/" + carryType.getServer() + "/carry-type/" + carryType.getId())
                .delete()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryType.fromJson(response.body().string()));
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to remove carry type {}.", carryType.getId(), ioException);
        }

        return Optional.empty();
    }

    public Optional<CarryType> updateCarryType(CarryType carryType) {
        RequestBody requestBody = new FormBody.Builder()
                .add("carryTypeJson", carryType.toJson())
                .build();

        Request request = getApiRequest("server/" + carryType.getServer() + "/carry-type")
                .put(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryType.fromJson(response.body().string()));
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to update carry type {}.", carryType.getId(), ioException);
        }

        return Optional.empty();
    }

    public Optional<CarryTier> getCarryTierFromCategory(long serverId, long categoryId) {
        Request request = getApiRequest("server/" + serverId + "/category/" + categoryId + "/carry-tier")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryTier.fromJson(response.body().string()));
            } else {
                if (response.code() != 404) {
                    if (response.body() != null) {
                        String responseBody = response.body().string();

                        logger.error(responseBody);
                    } else {
                        logger.error("Error while trying to load carry tier for category {}.", categoryId);
                    }
                }
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to load carry tier for category {}.", categoryId);
        }

        return Optional.empty();
    }

    public Optional<CarryTier> addNewCarryTier(CarryType carryType, String identifier, String displayName) {
        RequestBody requestBody = getRequestBody(identifier, displayName);

        Request request = getApiRequest("server/" + carryType.getServer() + "/carry-type/" + carryType.getId() + "/carry-tier")
                .post(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryTier.fromJson(response.body().string()));
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to add new carry tier {} for carry type {}.", identifier, carryType.getId(), ioException);
        }

        return Optional.empty();
    }

    public Optional<CarryTier> removeCarryTier(CarryTier carryTier) {
        //TODO implement
        return Optional.empty();
    }

    public Optional<CarryTier> updateCarryTier(CarryTier carryTier) {
        RequestBody requestBody = new FormBody.Builder()
                .add("carryTierJson", carryTier.toJson())
                .build();

        Request request = getApiRequest("server/" + carryTier.getCarryType().getServer() + "/carry-type/" + carryTier.getCarryType().getId() + "/carry-tier")
                .put(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return Optional.ofNullable(CarryTier.fromJson(response.body().string()));
            }
        }
        catch (IOException ioException) {
            logger.error("Error while trying to update carry tier {}.", carryTier.getId(), ioException);
        }

        return Optional.empty();
    }

    public boolean isCarryTypeExistant(long server, String identifier) {
        return loadCarryTypesForServer(server)
                .stream()
                .anyMatch(carryType -> carryType.getIdentifier().equalsIgnoreCase(identifier));
    }

    public boolean isCarryTierExistant(CarryType carryType, String identifier) {
        return loadCarryTiers(carryType)
                .stream()
                .anyMatch(carryTier -> carryTier.getIdentifier().equalsIgnoreCase(identifier));
    }

    public boolean isCarryDifficultyExistant(CarryTier carryTier, String identifier) {
        return loadCarryDifficulties(carryTier)
                .stream()
                .anyMatch(carryDifficulty -> carryDifficulty.getIdentifier().equalsIgnoreCase(identifier));
    }
}