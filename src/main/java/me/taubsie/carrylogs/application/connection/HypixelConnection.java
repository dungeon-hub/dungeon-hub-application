package me.taubsie.carrylogs.application.connection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.taubsie.dungeonhub.common.config.ConfigProperty;
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
import java.util.stream.Stream;

public class HypixelConnection {
    private static final Logger logger = LoggerFactory.getLogger(HypixelConnection.class);

    private static final long AUCTION_REFRESH_TIME = 1000L * 60;
    private static HypixelConnection instance;
    private final OkHttpClient httpClient;

    private final List<JsonObject> talismen = new ArrayList<>();

    private HypixelConnection() {
        httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                reloadTalismen();
            }
        }, new Time(System.currentTimeMillis()), AUCTION_REFRESH_TIME);
    }

    public static HypixelConnection getInstance() {
        if(instance == null) {
            instance = new HypixelConnection();
        }

        return instance;
    }

    public Map<String, String> getSkyCryptData(String ign) {
        Map<String, String> result = new HashMap<>();
        String url = "https://sky.shiiyu.moe/stats/" + ign;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.body() == null) {
                return new HashMap<>();
            }

            try(InputStream inputStream = response.body().byteStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder content = new StringBuilder();
                String line;

                while((line = bufferedReader.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());

                    if(line.equalsIgnoreCase("</head>") || line.contains("</head>")) {
                        break;
                    }
                }

                Document document = Jsoup.parse(content.toString());

                Element head = document.head();

                for(Element meta : head.getElementsByTag("meta")) {
                    switch(meta.attr("property").toLowerCase()) {
                        case "og:title" -> result.put("title", meta.attr("content"));
                        case "og:image" -> result.put("icon", meta.attr("content"));
                        case "og:description" -> result.put("description", meta.attr("content"));
                    }
                }
            }
        }
        catch(IOException ioException) {
            logger.error("Error when trying to load Skycrypt data for user {}.", ign, ioException);
        }

        if(result.getOrDefault("title", "SkyBlock Stats").equalsIgnoreCase("SkyBlock Stats")) {
            return new HashMap<>();
        }

        return result;
    }

    public List<JsonObject> getTalismen(boolean bin) {
        Stream<JsonObject> talismenData = talismen.stream();

        if(bin) {
            talismenData = talismenData.filter(jsonObject -> jsonObject.getAsJsonPrimitive("bin").getAsBoolean());
        }

        return talismenData.toList();
    }

    private void reloadTalismen() {
        List<JsonObject> newTalismenData = reloadTalismen(0, 0).toList();

        talismen.clear();

        talismen.addAll(newTalismenData);
    }

    //TODO isn't this the same as getUserDiscord() ?
    public String getHypixelLinkedDiscord(UUID uuid) {
        //TODO implement
        return "Taubsie#0911";
    }

    private Optional<String> getUserDiscord(UUID uuid) {
        String baseUrl = "https://api.hypixel.net/player?key=&uuid=";

        HttpUrl url = HttpUrl.get(baseUrl)
                .newBuilder()
                .addQueryParameter("key", ConfigProperty.HYPIXEL_API_KEY.getValue())
                .addQueryParameter("uuid", uuid.toString())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful() && response.body() != null) {
                JsonObject baseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();

                if(baseObject.getAsJsonPrimitive("success").getAsBoolean()) {
                    return Optional.ofNullable(baseObject.getAsJsonObject("player"))
                            .map(playerObject -> playerObject.getAsJsonObject("socialMedia"))
                            .map(socialObject -> socialObject.getAsJsonObject("links"))
                            .map(linksObject -> linksObject.getAsJsonPrimitive("DISCORD").getAsString());
                }
            }
        }
        catch(IOException ioException) {
            logger.error("Error when requesting discord user for uuid {}.", uuid, ioException);
        }

        return Optional.empty();
    }

    //TODO remove complexity
    private Stream<JsonObject> reloadTalismen(int page, int retry) {
        String baseUrl = "https://api.hypixel.net/skyblock/auctions?page=";

        Request request = new Request.Builder()
                .url(baseUrl + page)
                .get()
                .build();

        try(Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                if(retry < 3) {
                    return reloadTalismen(page, retry + 1);
                }

                if(response.code() != 404) {
                    logger.error("Unsuccessful auctions request: {}", response.code());
                }

                return Stream.empty();
            }

            JsonObject baseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
            List<JsonObject> result = new ArrayList<>();

            for(JsonElement jsonElement : baseObject.getAsJsonArray("auctions").asList()) {
                JsonObject auctionObject = jsonElement.getAsJsonObject();
                if(auctionObject.getAsJsonPrimitive("category").getAsString().equalsIgnoreCase("accessories")) {
                    NBTCompound nbtData =
                            NBTReader.readBase64(auctionObject.getAsJsonPrimitive("item_bytes").getAsString());

                    nbtData = nbtData.getList("i").getCompound(0);

                    nbtData = nbtData.getCompound("tag");

                    nbtData = nbtData.getCompound("ExtraAttributes");

                    if(nbtData.containsKey("rarity_upgrades")) {
                        result.add(auctionObject);
                    }
                }
            }

            if(baseObject.getAsJsonPrimitive("totalPages").getAsInt() <= page + 1) {
                return result.stream();
            }

            return Stream.concat(result.stream(), reloadTalismen(++page, 0));
        }
        catch(IOException ioException) {
            if(retry < 3) {
                return reloadTalismen(page, retry + 1);
            }

            logger.error("Auction request threw an error.", ioException);
            return Stream.empty();
        }
    }
}