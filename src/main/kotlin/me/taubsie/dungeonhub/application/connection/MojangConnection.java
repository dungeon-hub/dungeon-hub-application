package me.taubsie.dungeonhub.application.connection;

import com.google.gson.JsonParser;
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundException;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

public class MojangConnection {
    private static final Logger logger = LoggerFactory.getLogger(MojangConnection.class);
    private static MojangConnection instance;

    public static MojangConnection getInstance() {
        if (instance == null) {
            instance = new MojangConnection();
        }

        return instance;
    }

    public UUID fromString(String uuid) {
        //TODO check for uuid format
        return UUID.fromString(
                uuid.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                )
        );
    }

    //As this requests data from the Mojang API (aka slow), it is recommended to use UUIDs instead of names
    public UUID getUUIDByName(String name) throws PlayerNotFoundException {
        Request request = new Request.Builder()
                .url("https://api.mojang.com/users/profiles/minecraft/" + name)
                .get()
                .build();

        try (Response response = DungeonHubConnection.getInstance().getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return fromString(JsonParser.parseString(response.body().string()).getAsJsonObject().get("id").getAsString());
            }
        }
        catch (IOException | NullPointerException exception) {
            logger.error(null, exception);
        }

        throw new PlayerNotFoundException(name);
    }

    public String getNameByUUID(UUID uuid) throws PlayerNotFoundException {
        Request request = new Request.Builder()
                .url("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString())
                .get()
                .build();

        try (Response response = DungeonHubConnection.getInstance().getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return JsonParser.parseString(response.body().string()).getAsJsonObject().get("name").getAsString();
            }
        }
        catch (IOException | NullPointerException exception) {
            logger.error(null, exception);
        }

        throw new PlayerNotFoundException(uuid);
    }
}