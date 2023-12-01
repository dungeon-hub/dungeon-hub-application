package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.taubsie.dungeonhub.application.classes.FlagDetail;
import me.taubsie.dungeonhub.application.classes.FlagResponse;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.enums.FlaggingApi;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
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
        return Arrays.stream(FlaggingApi.values())
                .parallel()
                .map(flaggingApi -> flaggingApi.execute(uuid, id))
                .toList();
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

    public String[] isFlagged(String user, boolean discord) {
        HttpUrl httpUrl = HttpUrl.get(ConfigProperty.SAFETY_API_URL + "user")
                .newBuilder()
                .addQueryParameter("user", user)
                .addQueryParameter("type", discord ? "discord" : "uuid")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", ConfigProperty.SAFETY_API_KEY.getValue())
                .get()
                .build();

        try (Response response = DungeonHubConnection.getInstance().getHttpClient().newCall(request).execute()) {
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
}