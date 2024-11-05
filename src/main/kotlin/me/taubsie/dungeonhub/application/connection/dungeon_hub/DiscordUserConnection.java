package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.model.discord_user.DiscordUserModel;
import net.dungeonhub.model.discord_user.DiscordUserUpdateModel;
import net.dungeonhub.service.MoshiService;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DiscordUserConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(DiscordUserConnection.class);
    private static DiscordUserConnection instance;

    public static DiscordUserConnection getInstance() {
        if (instance == null) {
            instance = new DiscordUserConnection();
        }

        return instance;
    }

    @Override
    public String getModuleApiPrefix() {
        return "discord-users";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<Long> countLinkedUsers() {
        HttpUrl url = getApiUrl("count-linked").build();

        Request request = new Request.Builder().url(url).build();

        return executeRequest(request, Long::parseLong);
    }

    public Optional<DiscordUserModel> getById(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, DiscordUserModel.Companion::fromJson);
    }

    public Optional<DiscordUserModel> getLinkedById(long id) {
        return getById(id).filter(discordUserModel -> discordUserModel.getMinecraftId() != null);
    }

    public Optional<List<DiscordUserModel>> getAll() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getDiscordUserListMoshi()::fromJson);
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

        return executeRequest(request, DiscordUserModel.Companion::fromJson);
    }

    //TODO test
    public Optional<Integer> getCarryCount(long id, long guildId) {
        HttpUrl url = getApiUrl(id + "/carries/" + guildId).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, Integer::parseInt);
    }

    public Optional<DiscordUserModel> findUserByUuid(UUID uuid) {
        HttpUrl url = getApiUrl("find")
                .addQueryParameter("uuid", uuid.toString())
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, DiscordUserModel.Companion::fromJson);
    }
}