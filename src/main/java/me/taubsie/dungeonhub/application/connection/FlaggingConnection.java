package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonObject;
import me.taubsie.dungeonhub.application.classes.FlagDetail;
import me.taubsie.dungeonhub.application.classes.FlagResponse;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.kord.application.enums.FlaggingApi;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FlaggingConnection implements Connection {
    private static final Logger logger = LoggerFactory.getLogger(FlaggingConnection.class);
    private static FlaggingConnection instance;

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

        FlagDetail.FlagDetailBuilder builder = FlagDetail.builder()
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
        FlagDetail.FlagDetailBuilder builder = FlagDetail.builder()
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