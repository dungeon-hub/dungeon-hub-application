package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.ServerService;
import org.javacord.api.entity.Nameable;
import org.javacord.api.event.server.ServerLeaveEvent;
import org.javacord.api.listener.server.ServerLeaveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Listener
public class BotServerLeaveListener implements ServerLeaveListener {
    private static final Logger logger = LoggerFactory.getLogger(BotServerLeaveListener.class);

    @Override
    public void onServerLeave(ServerLeaveEvent serverLeaveEvent) {
        DiscordConnection.getInstance().resetBotAppearance();

        String ownerName = serverLeaveEvent.getServer().getOwner().map(Nameable::getName).orElse("no-name");
        logger.info("I just left server '{}' by '{}' ({}).",
                serverLeaveEvent.getServer().getName(),
                ownerName,
                serverLeaveEvent.getServer().getOwnerId());

        ApplicationService.getInstance()
                .getBotOwner(serverLeaveEvent.getApi())
                .openPrivateChannel().join()
                .sendMessage("I just left server `"
                        + serverLeaveEvent.getServer().getName()
                        + "` by "
                        + serverLeaveEvent.getServer().getOwner().map(Nameable::getName).map(s -> "`" + s + "`").orElse("no name")
                        + " ("
                        + serverLeaveEvent.getServer().getOwnerId()
                        + ").");

        ServerService.getInstance().unloadServerData(serverLeaveEvent.getServer().getId());
    }
}