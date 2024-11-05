package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.model.discord_role_group.DiscordRoleGroupModel;
import net.dungeonhub.service.MoshiService;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DiscordRoleGroupConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(DiscordRoleGroupConnection.class);
    private static final Map<Long, DiscordRoleGroupConnection> instances = new HashMap<>();
    private final long server;

    public DiscordRoleGroupConnection(long server) {
        this.server = server;
    }

    public static DiscordRoleGroupConnection getInstance(long server) {
        return instances.computeIfAbsent(server, DiscordRoleGroupConnection::new);
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + server + "/role-group";
    }

    public Optional<List<DiscordRoleGroupModel>> getAll() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url).get().build();

        return executeRequest(request, MoshiService.INSTANCE.getDiscordRoleGroupListMoshi()::fromJson);
    }
}