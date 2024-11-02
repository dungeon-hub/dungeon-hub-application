package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.model.discord_role.DiscordRoleCreationModel;
import net.dungeonhub.model.discord_role.DiscordRoleModel;
import net.dungeonhub.model.discord_role.DiscordRoleUpdateModel;
import net.dungeonhub.service.MoshiService;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DiscordRoleConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(DiscordRoleConnection.class);
    private static final Map<Long, DiscordRoleConnection> instances = new HashMap<>();
    private final long server;

    public DiscordRoleConnection(long server) {
        this.server = server;
    }

    public static DiscordRoleConnection getInstance(long server) {
        return instances.computeIfAbsent(server, DiscordRoleConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + server + "/roles";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<DiscordRoleModel> getById(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, DiscordRoleModel.Companion::fromJson);
    }

    public Optional<DiscordRoleModel> addNewRole(DiscordRoleCreationModel creationModel) {
        HttpUrl url = getApiUrl().build();

        RequestBody requestBody = RequestBody.create(
                creationModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .post(requestBody)
                .build();

        return executeRequest(request, DiscordRoleModel.Companion::fromJson);
    }

    public Optional<DiscordRoleModel> updateRole(long id, DiscordRoleUpdateModel updateModel) {
        HttpUrl url = getApiUrl(id).build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, DiscordRoleModel.Companion::fromJson);
    }

    public Optional<List<DiscordRoleModel>> getAllRoles() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url).get().build();

        return executeRequest(request, MoshiService.INSTANCE.getDiscordRoleListMoshi()::fromJson);
    }
}