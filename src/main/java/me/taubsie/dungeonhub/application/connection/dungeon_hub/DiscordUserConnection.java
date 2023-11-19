package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DiscordUserConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(DiscordUserConnection.class);
    private static DiscordUserConnection instance;

    @Override
    public String getModuleApiPrefix() {
        return "discord-users";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public static DiscordUserConnection getInstance() {
        if(instance == null) {
            instance = new DiscordUserConnection();
        }

        return instance;
    }

    public Optional<DiscordUserModel> getById(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, DiscordUserModel::fromJson);
    }

    public Optional<DiscordUserModel> updateUser(long id, DiscordUserUpdateModel updateModel) {
        HttpUrl url = getApiUrl(id).build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, DiscordUserModel::fromJson);
    }
}