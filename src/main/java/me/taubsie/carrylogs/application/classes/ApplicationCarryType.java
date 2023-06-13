package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryType;
import org.javacord.api.entity.server.Server;

import java.util.Optional;

public class ApplicationCarryType extends CarryType {
    public ApplicationCarryType(String id, String identifier, long server) {
        super(id, identifier, server);
    }

    public Optional<Server> getActualServer() {
        return BotStarter.getInstance().getBot().getServerById(getServer());
    }
}