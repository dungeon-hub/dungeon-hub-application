package me.taubsie.carrylogs.application.service;

import lombok.Getter;
import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.dungeonhub.common.CarryLogService;
import me.taubsie.dungeonhub.common.CarryRole;
import me.taubsie.dungeonhub.common.config.ConfigProperty;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Time;
import java.util.*;

public class ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);

    private static final String DUNGEON = "dungeon";
    private static final String SLAYER = "slayer";
    private static final String KUUDRA = "kuudra";

    private static final long REFRESH_TIME = 1000L * 60 * 55;
    private static ConnectionService instance;

    private final OkHttpClient httpClient;

    @Getter
    private String token;

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
                .url(ConfigProperty.API_URL + "token")
                .get()
                .addHeader("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                logger.error("Token-request wasn't successful!");
                return;
            }

            token = response.body().string();
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private Request.Builder getRequest(String uri) {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        return new Request.Builder()
                .url(ConfigProperty.API_URL + uri)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token);
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
        Request request = getRequest("v1/log-queue")
                .post(getRequestBody(id, carryInformation))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Added new carry to log-queue.");
            } else {
                logger.error("Adding new carry to log-queue wasn't successful");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void addToApprovingQueue(Long id, CarryInformation carryInformation) {
        Request request = getRequest("v1/approving-queue")
                .post(getRequestBody(id, carryInformation))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Added new carry to approving-queue.");
            } else {
                logger.error("Adding new carry to approving-queue wasn't successful.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void removeFromApprovingQueue(Long id) {
        Request request = getRequest("v1/approving-queue")
                .delete(getRequestBody(id))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Removed a carry from approving-queue.");
            } else {
                logger.error("Removing a carry from approving-queue wasn't successful.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void removeFromLogQueue(Long id) {
        Request request = getRequest("v1/log-queue")
                .delete(getRequestBody(id))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Removed a carry from log-queue.");
            } else {
                logger.error("Removing a carry from log-queue wasn't successful.");
            }
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public Set<CarryInformation> getFromLogApprovingQueue(Long id) {
        Request request = getRequest("v1/approving-queue/" + id)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Loaded carries from approving-queue.");

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
        Request request = getRequest("v1/log-queue/" + id)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Loaded carries from log-queue.");

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
        Request request = getRequest("v1/log")
                .post(getRequestBody(carryInformation))
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                logger.info("Logged carry successfully.");
                if(response.body() != null) {
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
        Request request = getRequest("v1/carry-score/" + id)
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

        return defaultMap;
    }

    public long getScore(Long id, String type) {
        Request request = getRequest("v1/carry-score/" + id + "/" + type)
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

    public Map<Long, Long> getLeaderboard(@NotNull String type) {
        Request request = getRequest("v1/leaderboard/" + type)
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
        return getLeaderboard(DUNGEON);
    }

    public Map<Long, Long> getSlayerLeaderboard() {
        return getLeaderboard(SLAYER);
    }

    public Map<Long, Long> getKuudraLeaderboard() {
        return getLeaderboard(KUUDRA);
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

        Request request = getRequest("v1/carry-score/" + id + "/" + type)
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

        Request request = getRequest("v1/roles")
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

        Request request = getRequest("v1/role")
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
        Request request = getRequest("v1/purge/" + type + "/" + amount)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()) {
                if(response.body() != null) {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(), CarryLogService.getInstance().getLongLongMapType());
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
}