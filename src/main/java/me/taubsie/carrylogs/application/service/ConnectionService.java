package me.taubsie.carrylogs.application.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.taubsie.carrylogs.application.exceptions.NotFoundException;
import me.taubsie.carrylogs.application.exceptions.PlayerNotFoundException;
import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.dungeonhub.common.CarryLogService;
import me.taubsie.dungeonhub.common.CarryRole;
import me.taubsie.dungeonhub.common.StrikeData;
import me.taubsie.dungeonhub.common.config.ConfigProperty;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Time;
import java.util.*;

//TODO maybe split up in different services for each api (internal, hypixel, ...)
public class ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);

    private static final String DUNGEON = "dungeon";
    private static final String SLAYER = "slayer";
    private static final String KUUDRA = "kuudra";
    private static final String ALLTIME_DUNGEON = "alltime-dungeon";
    private static final String ALLTIME_SLAYER = "alltime-slayer";
    private static final String ALLTIME_KUUDRA = "alltime-kuudra";
    private static final String EVENT_DUNGEON = "event-dungeon";
    private static final String EVENT_SLAYER = "event-slayer";

    private static final String API_PREFIX = "api/v1/";

    private static final int[] requiredXp = {50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700,
            17960, 25340, 35640, 50040, 70040, 97640, 135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640,
            1684640, 2284640, 3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640,
            39559640, 51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640,
            453559640, 569809640};

    private static final long REFRESH_TIME = 1000L * 60 * 55;
    private static ConnectionService instance;

    private final OkHttpClient httpClient;

    @Getter
    private String apiToken;

    private ConnectionService() {
        httpClient = new OkHttpClient();

        reloadToken();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                reloadToken();
            }
        }, new Time(System.currentTimeMillis() + REFRESH_TIME), REFRESH_TIME);
    }

    public static ConnectionService getInstance() {
        if(instance == null) {
            instance = new ConnectionService();
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
                .addHeader("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                logger.error("Token-request wasn't successful!");
                return;
            }

            apiToken = response.body().string();
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public String[] isFlagged(String user, boolean discord) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.SAFETY_API_URL + API_PREFIX + "user")
                .newBuilder()
                .addQueryParameter("user", user)
                .addQueryParameter("type", discord ? "discord" : "uuid")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", ConfigProperty.SAFETY_API_KEY.getValue())
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if(!response.isSuccessful() || responseBody == null) {
                return new String[0];
            }

            String body = responseBody.string();
            JsonObject responseObject = JsonParser.parseString(body).getAsJsonObject();
            responseObject = responseObject.getAsJsonObject("data");

            if(responseObject.has("scammer")) {
                return new String[]{
                        "Scammer",
                        responseObject
                                .getAsJsonObject("scammer")
                                .getAsJsonPrimitive("reason")
                                .getAsString()
                };
            } else if(responseObject.has("ratter")) {
                return new String[]{
                        "Ratter",
                        responseObject
                                .getAsJsonObject("ratter")
                                .getAsJsonPrimitive("reason")
                                .getAsString()
                };
            }
        }
        catch(IOException ioException) {
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
                .addHeader("Authorization", "Bearer " + apiToken);
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

    public void addToLogQueue(Long id, CarryInformation carryInformation) {
        Request request = getApiRequest("log-queue")
                .post(getRequestBody(id, carryInformation))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Added new carry to log-queue.");
            } else {
                logger.error("Adding new carry to log-queue wasn't successful");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void addToApprovingQueue(Long id, CarryInformation carryInformation) {
        Request request = getApiRequest("approving-queue")
                .post(getRequestBody(id, carryInformation))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Added new carry to approving-queue.");
            } else {
                logger.error("Adding new carry to approving-queue wasn't successful.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void removeFromApprovingQueue(Long id) {
        Request request = getApiRequest("approving-queue")
                .delete(getRequestBody(id))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Removed a carry from approving-queue.");
            } else {
                logger.error("Removing a carry from approving-queue wasn't successful.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void removeFromLogQueue(Long id) {
        Request request = getApiRequest("log-queue")
                .delete(getRequestBody(id))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Removed a carry from log-queue.");
            } else {
                logger.error("Removing a carry from log-queue wasn't successful.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public Set<CarryInformation> getFromLogApprovingQueue(Long id) {
        Request request = getApiRequest(getApiUrl("approving-queue")
                .addQueryParameter("id", String.valueOf(id))
                .build())
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Loaded carries from approving-queue.");

                if(response.body() == null) {
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
        catch(IOException ioException) {
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

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Loaded carries from log-queue.");

                if(response.body() == null) {
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
        catch(IOException ioException) {
            ioException.printStackTrace();
            return new HashSet<>();
        }
    }

    public long logCarry(CarryInformation carryInformation) {
        Request request = getApiRequest("log")
                .post(getRequestBody(carryInformation))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.debug("Logged carry successfully.");
                if(response.body() != null) {
                    //TODO nested try-block -> refactor or put into extra method
                    try {
                        return Long.parseLong(response.body().string());
                    }
                    catch(NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }
                }
            } else {
                logger.error("Error when trying to log carry.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return getScoreFromCarrier(carryInformation);
    }

    public long getScoreFromCarrier(CarryInformation carryInformation) {
        if(carryInformation.isDungeonCarry()) {
            return getDungeonScore(carryInformation.getCarrier());
        }

        if(carryInformation.isKuudraCarry()) {
            return getKuudraScore(carryInformation.getCarrier());
        }

        return getSlayerScore(carryInformation.getCarrier());
    }

    public Map<String, Long> countScore(Long id) {
        Request request = getApiRequest("carry-score/" + id)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getStringLongMapType());
                }
            } else {
                logger.error("Error when trying to count carries.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        Map<String, Long> defaultMap = new HashMap<>();

        defaultMap.put(DUNGEON, 0L);
        defaultMap.put(SLAYER, 0L);
        defaultMap.put(KUUDRA, 0L);
        defaultMap.put(ALLTIME_DUNGEON, 0L);
        defaultMap.put(ALLTIME_SLAYER, 0L);
        defaultMap.put(ALLTIME_KUUDRA, 0L);
        defaultMap.put(EVENT_DUNGEON, 0L);
        defaultMap.put(EVENT_SLAYER, 0L);

        return defaultMap;
    }

    public long getScore(Long id, String type) {
        Request request = getApiRequest("carry-score/" + id + "/" + type)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return Long.parseLong(response.body().string());
                }
            } else {
                logger.error("Error when trying to get score.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return 0L;
    }

    public long getKuudraScore(Long id) {
        return getScore(id, KUUDRA);
    }

    public long getDungeonScore(Long id) {
        return getScore(id, DUNGEON);
    }

    public long getSlayerScore(@NotNull Long id) {
        return getScore(id, SLAYER);
    }

    public Map<Long, Long> getLeaderboardData(@NotNull String type, int page) {
        Request request = getApiRequest(getApiUrl("leaderboard/" + type)
                .addQueryParameter("page", String.valueOf(page))
                .build())
                .get()
                .build();

        Map<Long, Long> result = new HashMap<>();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    result = CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getLongLongMapType());
                }
            } else {
                logger.error("Error when trying to get leaderboard.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return result;
    }

    public Map<Long, Long> getDungeonLeaderboard() {
        return getDungeonLeaderboard(1);
    }

    public Map<Long, Long> getDungeonLeaderboard(int page) {
        return getLeaderboardData(DUNGEON, page);
    }

    public Map<Long, Long> getAlltimeDungeonLeaderboard() {
        return getAlltimeDungeonLeaderboard(1);
    }

    public Map<Long, Long> getAlltimeDungeonLeaderboard(int page) {
        return getLeaderboardData(ALLTIME_DUNGEON, page);
    }

    public Map<Long, Long> getEventDungeonLeaderboard() {
        return getEventDungeonLeaderboard(1);
    }

    public Map<Long, Long> getEventDungeonLeaderboard(int page) {
        return getLeaderboardData(EVENT_DUNGEON, page);
    }

    public Map<Long, Long> getSlayerLeaderboard() {
        return getSlayerLeaderboard(1);
    }

    public Map<Long, Long> getSlayerLeaderboard(int page) {
        return getLeaderboardData(SLAYER, page);
    }

    public Map<Long, Long> getAlltimeSlayerLeaderboard() {
        return getAlltimeSlayerLeaderboard(1);
    }

    public Map<Long, Long> getAlltimeSlayerLeaderboard(int page) {
        return getLeaderboardData(ALLTIME_SLAYER, page);
    }

    public Map<Long, Long> getEventSlayerLeaderboard() {
        return getEventSlayerLeaderboard(1);
    }

    public Map<Long, Long> getEventSlayerLeaderboard(int page) {
        return getLeaderboardData(EVENT_SLAYER, page);
    }

    public Map<Long, Long> getKuudraLeaderboard() {
        return getKuudraLeaderboard(1);
    }

    public Map<Long, Long> getKuudraLeaderboard(int page) {
        return getLeaderboardData(KUUDRA, page);
    }

    public Map<Long, Long> getAlltimeKuudraLeaderboard() {
        return getAlltimeKuudraLeaderboard(1);
    }

    public Map<Long, Long> getAlltimeKuudraLeaderboard(int page) {
        return getLeaderboardData(ALLTIME_KUUDRA, page);
    }

    public long modifyDungeonScore(Long id, Long amount) {
        return modifyScore(id, DUNGEON, amount);
    }

    public long modifySlayerScore(Long id, Long amount) {
        return modifyScore(id, SLAYER, amount);
    }

    public long modifyKuudraScore(Long id, Long amount) {
        return modifyScore(id, KUUDRA, amount);
    }

    public long modifyScore(Long id, String type, Long amount) {
        RequestBody requestBody = new FormBody.Builder()
                .add("amount", String.valueOf(amount))
                .build();

        Request request = getApiRequest("carry-score/" + id + "/" + type)
                .put(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return Long.parseLong(response.body().string());
                }
            } else {
                logger.error("Error when trying to update score.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return 0L;
    }

    public void addMultipleRoles(Map<Long, List<CarryRole>> roleList) {
        RequestBody requestBody = new FormBody.Builder()
                .add("roles", CarryLogService.getInstance().getGson().toJson(roleList))
                .build();

        Request request = getApiRequest("roles")
                .put(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful()) {
                logger.error("Error when trying to add roles.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void addRoles(long id, List<CarryRole> roles) {
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
                if(!response.isSuccessful()) {
                    logger.error("Error when trying to add roles for user {}.", id);
                }
            }
        });
    }

    public Map<Long, Long> getPurgeableUsers(long amount, String type) {
        Request request = getApiRequest("purge/" + type + "/" + amount)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(),
                            CarryLogService.getInstance().getLongLongMapType());
                }
            } else {
                logger.error("Error when trying to load purgable users.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return new HashMap<>();
    }


    //As this requests data from the Mojang API (aka slow), it is recommended to use UUIDs instead of names
    public UUID getUUIDByName(String name) throws PlayerNotFoundException {
        Request request = new Request.Builder()
                .url("https://api.mojang.com/users/profiles/minecraft/" + name)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                return UUID.fromString(JsonParser.parseString(response.body().string()).getAsJsonObject().get("id").getAsString());
            }
        }
        catch(IOException | NullPointerException exception) {
            exception.printStackTrace();
        }

        throw new PlayerNotFoundException(name);
    }

    //This is a request on the Hypixel API, and therefore unneccessary calls should be avoided
    public int getCataLevelByUUID(UUID uuid) {
        JsonArray profiles = getProfiles(uuid);
        if(profiles == null) return 0;

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
            catch(NullPointerException ignored) {
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
        try(Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                logger.error("Unsuccessful profile request for UUID {}", uuid);
                return null;
            }

            return JsonParser.parseString(response.body().string()).getAsJsonObject().getAsJsonArray("profiles");
        }
        catch(IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private int cataXPToLevel(double xp) {
        for(int i = 0; i < requiredXp.length; i++) {
            if(requiredXp[i] > xp) return i;
        }

        // 50 and everything higher is returned as 50
        return 50;
    }

    //TODO rework with new uri (uses parameters)
    public StrikeData loadStrikeDataFromId(long serverId, long id) throws NotFoundException {
        Request request = getApiRequest(getApiUrl("strike/" + serverId)
                .addQueryParameter("id", String.valueOf(id)).build())
                .get().build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.code() == 404) {
                throw new NotFoundException();
            } else if(response.isSuccessful()) {
                if(response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(), StrikeData.class);
                }
            } else {
                logger.error("Error when trying to load strike by id.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        throw new NotFoundException();
    }

    public List<StrikeData> loadValidStrikeData(long serverId, long userId) {
        //TODO implement
        return new ArrayList<>();
    }

    public List<StrikeData> loadAllStrikeData(long serverId, long userId) {
        //TODO implement
        return new ArrayList<>();
    }

    public StrikeData insertStrikeData(StrikeData strikeData) {
        RequestBody requestBody = new FormBody.Builder()
                .add("strikeData", CarryLogService.getInstance().getGson().toJson(strikeData))
                .build();

        Request request = getApiRequest("strike")
                .post(requestBody)
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(), StrikeData.class);
                }
            } else {
                logger.error("Error when trying to insert strike.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return strikeData;
    }

    public void removeStrike(long serverId, long id) {
        //TODO implement
    }

    public int getMaxLeaderboardPage(String type) {
        Request request = getApiRequest("leaderboard/" + type + "/pages")
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return Integer.parseInt(response.body().string());
                }
            } else {
                logger.error("Error when trying to load the max leaderboard page.");
            }
        }
        catch(NumberFormatException numberFormatException) {
            logger.error("Couldn't parse number from return value (max leaderboard page).", numberFormatException);
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }

        return 1;
    }

    //TODO maybe extra endpoint with count() in database
    public int getMaxAllStrikePage(long serverId, long userId) {
        int entries = ConnectionService.getInstance()
                .loadAllStrikeData(serverId, userId)
                .size();

        return (int) Math.ceil(entries / 10.0);
    }

    //TODO maybe extra endpoint with count() in database
    public int getMaxValidStrikePage(long serverId, long userId) {
        int entries = ConnectionService.getInstance()
                .loadValidStrikeData(serverId, userId)
                .size();

        return (int) Math.ceil(entries / 10.0);
    }

    public String getHypixelLinkedDiscord(UUID uuid) {
        //TODO implement
        return "Taubsie#0911";
    }
}
