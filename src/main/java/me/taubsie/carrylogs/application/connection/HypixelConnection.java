package me.taubsie.carrylogs.application.connection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.taubsie.carrylogs.application.exceptions.FailedToLoadException;
import me.taubsie.dungeonhub.common.config.ConfigProperty;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.http.HypixelHttpClient;
import net.hypixel.api.http.HypixelHttpResponse;
import net.hypixel.api.http.RateLimit;
import net.hypixel.api.reply.PlayerReply;
import okhttp3.HttpUrl;
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
import java.sql.Time;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class HypixelConnection implements HypixelHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(HypixelConnection.class);

    private static final long AUCTION_REFRESH_TIME = 1000L * 60;
    private static HypixelConnection instance;
    private final OkHttpClient httpClient;
    private final HypixelAPI hypixelApi;

    private final List<JsonObject> talismen = new ArrayList<>();
    private final List<JsonObject> auctions = new ArrayList<>();

    private HypixelConnection() {
        httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        hypixelApi = new HypixelAPI(this);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                reloadTalismen();
                reloadAuctions();
            }
        }, new Time(System.currentTimeMillis()), AUCTION_REFRESH_TIME);
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


    public List<JsonObject> getTalismen(boolean bin) {
        Stream<JsonObject> talismenData = talismen.stream();

        if (bin) {
            talismenData = talismenData.filter(jsonObject -> jsonObject.getAsJsonPrimitive("bin").getAsBoolean());
        }

        return talismenData.toList();
    }

    private void reloadAuctions() {
        auctions.clear();

        auctions.addAll(loadAuctions(0).join());
    }

    private void reloadTalismen() {
        List<JsonObject> newTalismenData = reloadTalismen(0, 0).toList();

        talismen.clear();

        talismen.addAll(newTalismenData);
    }

    //TODO isn't this the same as getUserDiscord() ?
    public Optional<String> getHypixelLinkedDiscord(UUID uuid) {
        PlayerReply playerReply = hypixelApi.getPlayerByUuid(uuid).join();

        return Optional.ofNullable(playerReply.getPlayer())
                .map(player -> player.getObjectProperty("socialMedia"))
                .map(jsonObject -> jsonObject.getAsJsonObject("links"))
                .map(jsonObject -> jsonObject.getAsJsonPrimitive("DISCORD"))
                .map(JsonPrimitive::getAsString);
    }

    private Optional<String> getUserDiscord(UUID uuid) {
        String baseUrl = "https://api.hypixel.net/player";

        HttpUrl url = HttpUrl.get(baseUrl)
                .newBuilder()
                .addQueryParameter("uuid", uuid.toString())
                .build();

        Request request = new Request.Builder()
                .addHeader("API-Key", ConfigProperty.HYPIXEL_API_KEY.getValue())
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject baseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();

                if (baseObject.getAsJsonPrimitive("success").getAsBoolean()) {
                    return Optional.ofNullable(baseObject.getAsJsonObject("player"))
                            .map(playerObject -> playerObject.getAsJsonObject("socialMedia"))
                            .map(socialObject -> socialObject.getAsJsonObject("links"))
                            .map(linksObject -> linksObject.getAsJsonPrimitive("DISCORD").getAsString());
                }
            }
        }
        catch (IOException ioException) {
            logger.error("Error when requesting discord user for uuid {}.", uuid, ioException);
        }

        return Optional.empty();
    }

    //TODO test if the retry code even works
    //TODO use this instead of reloadTalismen()
    private CompletableFuture<Set<JsonObject>> loadAuctions(int page) {
        return hypixelApi.getSkyBlockAuctions(page)
                .thenApply(reply -> {
                    Set<JsonObject> jsonObjects = new HashSet<>();

                    for(JsonElement jsonElement : reply.getAuctions().asList()) {
                        if (jsonElement.isJsonObject()) {
                            jsonObjects.add(jsonElement.getAsJsonObject());
                        }
                    }

                    if (reply.hasNextPage()) {
                        jsonObjects.addAll(loadAuctions(page + 1).join());
                    }

                    return jsonObjects;
                });
    }

    //TODO remove complexity
    private Stream<JsonObject> reloadTalismen(int page, int retry) {
        String baseUrl = "https://api.hypixel.net/skyblock/auctions?page=";

        Request request = new Request.Builder()
                .url(baseUrl + page)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                if (retry < 3) {
                    return reloadTalismen(page, retry + 1);
                }

                if (response.code() != 404) {
                    logger.error("Unsuccessful auctions request: {}", response.code());
                }

                return Stream.empty();
            }

            JsonObject baseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
            List<JsonObject> result = new ArrayList<>();

            for(JsonElement jsonElement : baseObject.getAsJsonArray("auctions").asList()) {
                JsonObject auctionObject = jsonElement.getAsJsonObject();
                if (auctionObject.getAsJsonPrimitive("category").getAsString().equalsIgnoreCase("accessories")) {
                    NBTCompound nbtData =
                            NBTReader.readBase64(auctionObject.getAsJsonPrimitive("item_bytes").getAsString());

                    nbtData = nbtData.getList("i").getCompound(0);

                    nbtData = nbtData.getCompound("tag");

                    nbtData = nbtData.getCompound("ExtraAttributes");

                    if (nbtData.containsKey("rarity_upgrades")) {
                        result.add(auctionObject);
                    }
                }
            }

            if (baseObject.getAsJsonPrimitive("totalPages").getAsInt() <= page + 1) {
                return result.stream();
            }

            return Stream.concat(result.stream(), reloadTalismen(++page, 0));
        }
        catch (IOException ioException) {
            if (retry < 3) {
                return reloadTalismen(page, retry + 1);
            }

            logger.error("Auction request threw an error.", ioException);
            return Stream.empty();
        }
    }
}