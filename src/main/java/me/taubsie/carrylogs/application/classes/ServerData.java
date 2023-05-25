package me.taubsie.carrylogs.application.classes;

import me.taubsie.dungeonhub.common.CarryLogService;
import me.taubsie.dungeonhub.common.config.ConfigFile;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class holds all config properties for a server.
 * While this is an important class, it is only really used by
 * {@link me.taubsie.carrylogs.application.service.ServerService} and {@link ServerProperty}, as they offer a cleaner
 * API.
 * @see me.taubsie.carrylogs.application.service.ServerService
 * @see ServerProperty
 */
public class ServerData extends ConfigFile<ServerProperty> {
    private final long id;

    public ServerData(long id) {
        this.id = id;

        reloadConfig();
    }

    public long getId() {
        return id;
    }

    @Override
    protected Set<ServerProperty> getPossibleProperties() {
        return Arrays.stream(ServerProperty.values())
                .filter(ServerProperty::isEnabled)
                .collect(Collectors.toSet());
    }

    public boolean isEnabled(ServerProperty serverProperty) {
        //TODO implement logic
        return true;
    }

    public static String getConfigFolder() {
        return CarryLogService.getInstance().getMainFolder() + File.separator + "servers";
    }

    public String getServerFolder() {
        return getConfigFolder() + File.separator + id;
    }

    @Override
    protected File getConfigFile() {
        return new File(getServerFolder() + File.separator + "config.properties");
    }
}