package me.taubsie.dungeonhub.application.classes;

import lombok.Getter;
import me.taubsie.dungeonhub.application.service.ServerService;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.application.config.ConfigFile;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class holds all config properties for a server.
 * While this is an important class, it is only really used by
 * {@link ServerService} and {@link ServerProperty}, as they offer a cleaner
 * API.
 * @see ServerService
 * @see ServerProperty
 */
@Getter
public class ServerData extends ConfigFile<ServerProperty> {
    private final long id;

    public ServerData(long id) {
        this.id = id;

        reloadConfig();
    }

    @Override
    protected Set<ServerProperty> getPossibleProperties() {
        return Arrays.stream(ServerProperty.values())
                .filter(ServerProperty::isEnabled)
                .collect(Collectors.toSet());
    }

    public boolean isEnabled(ServerProperty serverProperty) {
        return serverProperty.isEnabled(id);
    }

    public static String getConfigFolder() {
        return DungeonHubService.getInstance().getMainFolder() + File.separator + "servers";
    }

    public String getServerFolder() {
        return getConfigFolder() + File.separator + id;
    }

    @Override
    protected File getConfigFile() {
        return new File(getServerFolder() + File.separator + "config.properties");
    }
}