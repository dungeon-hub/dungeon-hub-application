package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ServerService;
import me.taubsie.carrylogs.application.start.BotStarter;
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
        BotStarter.getInstance().resetBotAppearance();

        logger.info("I just joined server '{}' by '{}' ({}).",
                serverJoinEvent.getServer().getName(),
                serverJoinEvent.getServer().getOwner().map(Nameable::getName).orElse("no-name"),
                serverJoinEvent.getServer().getOwnerId());

        ApplicationService.getInstance()
                .getBotOwner(serverJoinEvent.getApi())
                .openPrivateChannel().join()
                .sendMessage("I just joined server '"
                        + serverJoinEvent.getServer().getName()
                        + "' by '"
                        + serverJoinEvent.getServer().getOwner().map(Nameable::getName).orElse("no-name")
                        + "' ("
                        + serverJoinEvent.getServer().getOwnerId()
                        + ").");

        ServerService.getInstance().loadServerData(serverJoinEvent.getServer().getId());
    }
}