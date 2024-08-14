package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.enums.FlaggingApi;
import me.taubsie.dungeonhub.application.misc.FlagDetail;
import me.taubsie.dungeonhub.application.misc.FlagResponse;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FlaggingConnection implements Connection {
    private static final Logger logger = LoggerFactory.getLogger(FlaggingConnection.class);
    private static FlaggingConnection instance;

    private Instant lastBlockGameRefresh;
    private List<JsonObject> blockGameData;

    public static FlaggingConnection getInstance() {
        if (instance == null) {
            instance = new FlaggingConnection();
        }

        return instance;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public List<FlagResponse> isFlagged(Long id) {
        return isFlagged(null, id);
    }

    public List<FlagResponse> isFlagged(UUID uuid) {
        return isFlagged(uuid, null);
    }

    public List<FlagResponse> isFlagged(UUID uuid, Long id) {
        return FlaggingApi.getEntries().stream()
                .parallel()
                .map(flaggingApi -> flaggingApi.execute(uuid, id))
                .toList();
    }

    public Optional<FlagDetail> isBlockGameFlagged(Long id) {
        if (lastBlockGameRefresh == null
                || blockGameData == null
                || lastBlockGameRefresh.isBefore(Instant.now().minus(5, ChronoUnit.MINUTES))) {
            refreshBlockGameData();
        }

        return blockGameData.parallelStream()
                .filter(jsonObject -> jsonObject.has("id")
                        && jsonObject.get("id").isJsonPrimitive()
                        && jsonObject.getAsJsonPrimitive("id").getAsLong() == id)
                .findFirst()
                .map(this::loadFlagDetailFromBlockGameData);
    }

    private FlagDetail loadFlagDetailFromBlockGameData(JsonObject blockGameData) {
        StringBuilder reason = new StringBuilder();

        JsonPrimitive scammedAmount = blockGameData.getAsJsonPrimitive("scammed");
        JsonPrimitive method = blockGameData.getAsJsonPrimitive("method");

        if(scammedAmount != null && scammedAmount.isString()) {
            reason.append("(").append(scammedAmount.getAsString()).append(")");

            if(method != null && method.isString()) {
                reason.append(" -> ");
            }
        }

        if(method != null && method.isString()) {
            reason.append(method.getAsString());
        }


        return FlagDetail.FlagDetailBuilder.builder()
                .flagged(true)
                .reason(reason.toString())
                .build();
    }

    public void refreshBlockGameData() {
        lastBlockGameRefresh = Instant.now();

        HttpUrl httpUrl = HttpUrl.get("https://block.lenny.ie/scammers");

        Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .build();

        JsonArray jsonArray = executeRequest(request, s -> fromJson(s, JsonArray.class))
                .orElse(null);

        if (jsonArray == null) {
            return;
        }

        blockGameData = jsonArray.asList().parallelStream()
                .map(JsonElement::getAsJsonObject)
                .toList();
    }

    public Optional<FlagDetail> isSafetyFlagged(UUID uuid) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.SAFETY_API_URL + "v1/user")
                .newBuilder()
                .addQueryParameter("user", uuid.toString())
                .addQueryParameter("type", "uuid")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", ConfigProperty.SAFETY_API_KEY.getValue())
                .get()
                .build();

        return executeRequest(request, s -> fromJson(s, JsonObject.class))
                .map(this::fromSafetyResponse);
    }

    public Optional<FlagDetail> isSafetyFlagged(Long id) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.SAFETY_API_URL + "v1/user")
                .newBuilder()
                .addQueryParameter("user", String.valueOf(id))
                .addQueryParameter("type", "discord")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", ConfigProperty.SAFETY_API_KEY.getValue())
                .get()
                .build();

        return executeRequest(request, s -> fromJson(s, JsonObject.class))
                .map(this::fromSafetyResponse);
    }

    public FlagDetail fromSafetyResponse(JsonObject rootObject) {
        JsonObject dataObject = rootObject.getAsJsonObject("data");

        boolean flagged = dataObject.has("ratter") || dataObject.has("scammer");

        FlagDetail.Builder builder = FlagDetail.FlagDetailBuilder.builder()
                .flagged(flagged);

        JsonObject detailObject = null;
        if (dataObject.has("ratter")) {
            detailObject = dataObject.getAsJsonObject("ratter");
        } else if (dataObject.has("scammer")) {
            detailObject = dataObject.getAsJsonObject("scammer");
        }

        if (detailObject != null) {
            builder.reason(detailObject.getAsJsonPrimitive("reason").getAsString());

            if (detailObject.has("evidence")) {
                List<String> evidences = new ArrayList<>();
                detailObject.getAsJsonArray("evidence")
                        .forEach(jsonElement -> evidences.add(jsonElement.getAsString()));

                builder.evidence(String.join(", ", evidences));
            }

            if (detailObject.has("moderator")) {
                try {
                    builder.staff(detailObject.getAsJsonPrimitive("moderator").getAsLong());
                }
                catch (NumberFormatException ignored) {
                    //ignored since this basically only applies if the id isn't a number, meaning this shouldn't be set
                }
            }
        }

        return builder.build();
    }

    public Optional<FlagDetail> isJerryFlagged(UUID uuid) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.JERRY_API_URL + "v1/scammers/" + uuid);

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
                .get()
                .build();

        return executeRequest(request, s -> fromJson(s, JsonObject.class))
                .filter(jsonObject -> jsonObject.get("success").getAsBoolean())
                .map(this::fromJerryResponse);
    }

    public Optional<FlagDetail> isJerryFlagged(Long id) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.JERRY_API_URL + "v1/scammers/discord/" + id);

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
                .get()
                .build();

        return executeRequest(request, s -> fromJson(s, JsonObject.class))
                .filter(jsonObject -> jsonObject.get("success").getAsBoolean())
                .map(this::fromJerryResponse);
    }

    public FlagDetail fromJerryResponse(JsonObject rootObject) {
        FlagDetail.Builder builder = FlagDetail.FlagDetailBuilder.builder()
                .flagged(rootObject.get("scammer").getAsBoolean());

        if (rootObject.has("details") && rootObject.get("details").isJsonObject()) {
            JsonObject detailObject = rootObject.getAsJsonObject("details");

            if (detailObject.has("reason")) {
                builder.reason(detailObject.getAsJsonPrimitive("reason").getAsString());
            }

            if (detailObject.has("staff")) {
                try {
                    builder.staff(detailObject.getAsJsonPrimitive("staff").getAsLong());
                }
                catch (NumberFormatException ignored) {
                    //ignored since this basically only applies if the id is redacted, meaning this shouldn't be set
                }
            }

            if (detailObject.has("evidence")) {
                String evidence = detailObject.getAsJsonPrimitive("evidence").getAsString();
                if (!evidence.equalsIgnoreCase("<redacted>")) {
                    builder.evidence(evidence);
                }
            }
        }

        return builder.build();
    }
}