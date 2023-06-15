package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryType;
import org.javacord.api.entity.server.Server;

import java.util.Optional;

public class ApplicationCarryType extends CarryType {
    public ApplicationCarryType(long id, String identifier, long server, long logChannel) {
        super(id, identifier, server, logChannel);
    }

    public Optional<Server> getActualServer() {
        return BotStarter.getInstance().getBot().getServerById(getServer());
    }
}