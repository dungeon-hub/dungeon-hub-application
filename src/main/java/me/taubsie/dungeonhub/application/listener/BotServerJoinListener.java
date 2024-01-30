package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.ServerService;
import org.javacord.api.entity.Nameable;
import org.javacord.api.event.server.ServerJoinEvent;
import org.javacord.api.listener.server.ServerJoinListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Listener
public class BotServerJoinListener implements ServerJoinListener {
    private static final Logger logger = LoggerFactory.getLogger(BotServerJoinListener.class);

    @Override
    public void onServerJoin(ServerJoinEvent serverJoinEvent) {
        DiscordConnection.getInstance().resetBotAppearance();

        String ownerName = serverJoinEvent.getServer().getOwner().map(Nameable::getName).orElse("no-name");
        logger.info("I just joined server '{}' by '{}' ({}).",
                serverJoinEvent.getServer().getName(),
                ownerName,
                serverJoinEvent.getServer().getOwnerId());

        ApplicationService.getInstance()
                .getBotOwner(serverJoinEvent.getApi())
                .openPrivateChannel().join()
                .sendMessage("I just joined server `"
                        + serverJoinEvent.getServer().getName()
                        + "` by "
                        + serverJoinEvent.getServer().getOwner().map(Nameable::getName).map(s -> "`" + s + "`").orElse("no name")
                        + " (<@"
                        + serverJoinEvent.getServer().getOwnerId()
                        + ">).");

        ServerService.getInstance().loadServerData(serverJoinEvent.getServer().getId());
    }
}