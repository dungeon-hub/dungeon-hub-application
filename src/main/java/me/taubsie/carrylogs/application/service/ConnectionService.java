package me.taubsie.carrylogs.application.service;

import lombok.Getter;
import me.taubsie.carrylogs.CarryInformation;
import me.taubsie.carrylogs.CarryLogService;
import me.taubsie.carrylogs.config.ConfigProperty;
import okhttp3.*;

import java.io.IOException;
import java.sql.Time;
import java.util.*;

//TODO add logging
public class ConnectionService
{
    private static final long REFRESH_TIME = 1000 * 60 * 55;

    private static ConnectionService instance;

    private final OkHttpClient httpClient;

    @Getter
    private String token;

    public static ConnectionService getInstance()
    {
        if (instance == null)
        {
            instance = new ConnectionService();
        }

        return instance;
    }

    private ConnectionService()
    {
        httpClient = new OkHttpClient();

        reloadToken();

        new Timer().scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                reloadToken();
            }
        }, new Time(System.currentTimeMillis() + REFRESH_TIME), REFRESH_TIME);
    }

    /**
     * Do not call this outside of constructor or the scheduled run.
     */
    private void reloadToken()
    {
        String username = ConfigProperty.API_USER.getValue();
        String password = ConfigProperty.API_PASSWORD.getValue();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "token")
                .get()
                .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                System.out.println("Token-request wasn't successful!");
                return;
            }

            token = response.body().string();
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    public void addToLogQueue(Long id, CarryInformation carryInformation)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");
        RequestBody requestBody = new FormBody.Builder()
                .add("id", id.toString())
                .add("carryInformation", carryInformation.toJson())
                .build();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/log-queue")
                .post(requestBody)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Added new carry to log-queue.");
            }
            else
            {
                System.out.println("Adding new carry to log-queue wasn't successful.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    public void addToApprovingQueue(Long id, CarryInformation carryInformation)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");
        RequestBody requestBody = new FormBody.Builder()
                .add("id", id.toString())
                .add("carryInformation", carryInformation.toJson())
                .build();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/approving-queue")
                .post(requestBody)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Added new carry to approving-queue.");
            }
            else
            {
                System.out.println("Adding new carry to approving-queue wasn't successful.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    public void removeFromApprovingQueue(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");
        RequestBody requestBody = new FormBody.Builder()
                .add("id", id.toString())
                .build();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/approving-queue")
                .delete(requestBody)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Removed a carry from approving-queue.");
            }
            else
            {
                System.out.println("Removing a carry from approving-queue wasn't successful.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    public void removeFromLogQueue(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");
        RequestBody requestBody = new FormBody.Builder()
                .add("id", id.toString())
                .build();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/log-queue")
                .delete(requestBody)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Removed a carry from log-queue.");
            }
            else
            {
                System.out.println("Removing a carry from log-queue wasn't successful.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    public Set<CarryInformation> getFromLogApprovingQueue(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/approving-queue/" + id)
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Loaded carries from approving-queue.");

                if (response.body() == null)
                {
                    return new HashSet<>();
                }

                return CarryLogService.getInstance().getGson().fromJson(response.body().string(), CarryLogService.getInstance().getCarryInformationSetType());
            }
            else
            {
                if (response.body() != null)
                {
                    System.out.println(response.body().string());
                }

            }
            return new HashSet<>();
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
            return new HashSet<>();
        }
    }

    public Set<CarryInformation> getFromLogQueue(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/log-queue/" + id)
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Loaded carries from log-queue.");

                if (response.body() == null)
                {
                    return new HashSet<>();
                }

                return CarryLogService.getInstance().getGson().fromJson(response.body().string(), CarryLogService.getInstance().getCarryInformationSetType());
            }
            else
            {
                if (response.body() != null)
                {
                    System.out.println(response.body().string());
                }

            }
            return new HashSet<>();
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
            return new HashSet<>();
        }
    }

    public long logCarry(CarryInformation carryInformation)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        RequestBody requestBody = new FormBody.Builder()
                .add("carryInformation", carryInformation.toJson())
                .build();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/log")
                .post(requestBody)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                System.out.println("Logged carry successfully.");
                if (response.body() != null)
                {
                    try
                    {
                        return Long.parseLong(response.body().string());
                    }
                    catch (NumberFormatException numberFormatException)
                    {
                        numberFormatException.printStackTrace();
                    }
                }
            }
            else
            {
                System.out.println("Error when trying to log carry.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return getScoreFromCarrier(carryInformation);
    }

    public long getScoreFromCarrier(CarryInformation carryInformation)
    {
        return carryInformation.isDungeonCarry()
                ? getDungeonScore(carryInformation.getCarrier())
                : getSlayerScore(carryInformation.getCarrier());
    }

    public Map<String, Long> countScore(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/carry-score/" + id)
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                if (response.body() != null)
                {
                    return CarryLogService.getInstance().getGson().fromJson(response.body().string(), CarryLogService.getInstance().getStringLongMapType());
                }
            }
            else
            {
                System.out.println("Error when trying to count carries.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        Map<String, Long> defaultMap = new HashMap<>();

        defaultMap.put("dungeon", 0L);
        defaultMap.put("slayer", 0L);

        return defaultMap;
    }

    public long getDungeonScore(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/carry-score/" + id + "/dungeon")
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                if (response.body() != null)
                {
                    return Long.parseLong(response.body().string());
                }
            }
            else
            {
                System.out.println("Error when trying to count carries.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return 0L;
    }

    public long getSlayerScore(Long id)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/carry-score/" + id + "/slayer")
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                if (response.body() != null)
                {
                    return Long.parseLong(response.body().string());
                }
            }
            else
            {
                System.out.println("Error when trying to count carries.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return 0L;
    }

    public Map<Long, Long> getDungeonLeaderboard()
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/leaderboard/dungeon")
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        Map<Long, Long> result = new HashMap<>();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                if (response.body() != null)
                {
                    result = CarryLogService.getInstance().getGson().fromJson(response.body().string(), CarryLogService.getInstance().getLongLongMapType());
                }
            }
            else
            {
                System.out.println("Error when trying to get leaderboard.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return result;
    }

    public Map<Long, Long> getSlayerLeaderboard()
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/leaderboard/slayer")
                .get()
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        Map<Long, Long> result = new HashMap<>();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                if (response.body() != null)
                {
                    result = CarryLogService.getInstance().getGson().fromJson(response.body().string(), CarryLogService.getInstance().getLongLongMapType());
                }
            }
            else
            {
                System.out.println("Error when trying to get leaderboard.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return result;
    }

    public long modifyDungeonScore(Long id, Long amount)
    {
        return modifyScore(id, "dungeons", amount);
    }

    public long modifySlayerScore(Long id, Long amount)
    {
        return modifyScore(id, "slayer", amount);
    }

    public long modifyScore(Long id, String type, Long amount)
    {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        RequestBody requestBody = new FormBody.Builder()
                .add("amount", String.valueOf(amount))
                .build();

        Request request = new Request.Builder()
                .url(ConfigProperty.API_URL + "v1/carry-score/" + id + "/" + type)
                .put(requestBody)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                if (response.body() != null)
                {
                    return Long.parseLong(response.body().string());
                }
            }
            else
            {
                System.out.println("Error when trying to update score.");
            }
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return 0L;
    }
}