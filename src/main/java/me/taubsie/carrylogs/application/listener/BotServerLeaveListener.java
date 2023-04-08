package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ServerService;
import me.taubsie.carrylogs.application.start.BotStarter;
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
        BotStarter.getInstance().resetBotAppearance();

        logger.info("I just left server '{}' by '{}' ({}).",
                serverLeaveEvent.getServer().getName(),
                serverLeaveEvent.getServer().getOwner().map(Nameable::getName).orElse("no-name"),
                serverLeaveEvent.getServer().getOwnerId());

        ServerService.getInstance().unloadServerData(serverLeaveEvent.getServer().getId());
    }
}