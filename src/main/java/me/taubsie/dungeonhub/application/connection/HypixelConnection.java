package me.taubsie.dungeonhub.application.connection;

import com.google.gson.*;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadException;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.http.RateLimit;
import net.hypixel.api.reply.PlayerReply;
import net.hypixel.api.reply.StatusReply;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HypixelConnection implements HypixelHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(HypixelConnection.class);

    private static final int[] requiredXp = {50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700,
            17960, 25340, 35640, 50040, 70040, 97640, 135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640,
            1684640, 2284640, 3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640,
            39559640, 51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640,
            453559640, 569809640};

    private static HypixelConnection instance;
    private final OkHttpClient httpClient;
    private final HypixelAPI hypixelApi;

    private HypixelConnection() {
        httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        hypixelApi = new HypixelAPI(this);
    }

    public static HypixelConnection getInstance() {
        if (instance == null) {
            instance = new HypixelConnection();
        }

        return instance;
    }

    @Override
    public CompletableFuture<HypixelHttpResponse> makeRequest(String url) {
        return new CompletableFuture<HypixelHttpResponse>().completeAsync(() -> {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() != null) {
                    return new HypixelHttpResponse(response.code(), response.body().string(), null);
                }
            }
            catch (IOException ioException) {
                logger.error("Error when performing unauthenticated hypixel request");
            }

            throw new FailedToLoadException("Hypixel request wasn't successful.");
        });
    }

    @Override
    public CompletableFuture<HypixelHttpResponse> makeAuthenticatedRequest(String url) {
        return new CompletableFuture<HypixelHttpResponse>().completeAsync(() -> {
            Request request = new Request.Builder()
                    .addHeader("API-Key", ConfigProperty.HYPIXEL_API_KEY.getValue())
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() != null) {
                    return new HypixelHttpResponse(response.code(), response.body().string(),
                            createRateLimitResponse(response));
                }
            }
            catch (IOException ioException) {
                logger.error("Error when performing authenticated hypixel request {}.", url, ioException);
            }

            throw new FailedToLoadException("Hypixel request wasn't successful.");
        });
    }

    @Override
    public void shutdown() {
        //not needed, happens separately
    }

    private RateLimit createRateLimitResponse(Response response) {
        if (response.code() != 200) {
            return null;
        }

        int limit = Integer.parseInt(Objects.requireNonNull(response.header("RateLimit-Limit")));
        int remaining = Integer.parseInt(Objects.requireNonNull(response.header("RateLimit-Remaining")));
        int reset = Integer.parseInt(Objects.requireNonNull(response.header("RateLimit-Reset")));
        return new RateLimit(limit, remaining, reset);
    }


    public Map<String, String> getSkyCryptData(String ign) {
        Map<String, String> result = new HashMap<>();
        String url = "https://sky.shiiyu.moe/stats/" + ign;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                return new HashMap<>();
            }

            try (InputStream inputStream = response.body().byteStream();
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder content = new StringBuilder();
                String line;

                while((line = bufferedReader.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());

                    if (line.equalsIgnoreCase("</head>") || line.contains("</head>")) {
                        break;
                    }
                }

                Document document = Jsoup.parse(content.toString());

                Element head = document.head();

                for(Element meta : head.getElementsByTag("meta")) {
                    switch (meta.attr("property").toLowerCase()) {
                        case "og:title" -> result.put("title", meta.attr("content"));
                        case "og:image" -> result.put("icon", meta.attr("content"));
                        case "og:description" -> result.put("description", meta.attr("content"));
                        default -> {
                        }
                    }
                }
            }
        }
        catch (IOException ioException) {
            logger.error("Error when trying to load Skycrypt data for user {}.", ign, ioException);
        }

        if (result.getOrDefault("title", "SkyBlock Stats").equalsIgnoreCase("SkyBlock Stats")) {
            return new HashMap<>();
        }

        return result;
    }

    public Optional<String> getHypixelLinkedDiscord(UUID uuid) {
        PlayerReply playerReply = hypixelApi.getPlayerByUuid(uuid).join();

        return Optional.ofNullable(playerReply.getPlayer())
                .map(player -> player.getObjectProperty("socialMedia"))
                .map(jsonObject -> jsonObject.getAsJsonObject("links"))
                .map(jsonObject -> jsonObject.getAsJsonPrimitive("DISCORD"))
                .map(JsonPrimitive::getAsString);
    }

    public StatusReply.Session getOnlineStatus(String ign) {
        UUID uuid = MojangConnection.getInstance().getUUIDByName(ign);

        return hypixelApi.getStatus(uuid).join().getSession();
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
                .addHeader("API-Key", ConfigProperty.HYPIXEL_API_KEY.getValue())
                .url("https://api.hypixel.net/skyblock/profiles?uuid=" + uuid)
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
}