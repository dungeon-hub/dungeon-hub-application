package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.StrikeData;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;

import java.util.List;
import java.util.Optional;

public class StrikeMessage extends PageableMessage {
    private final long userId;

    public StrikeMessage(int currentPage, long channel, long messageId, long userId) {
        super(currentPage, channel, messageId);

        this.userId = userId;
    }

    @Override
    public int getMaxPage() {
        return getServer()
                .map(DiscordEntity::getId)
                .map(server -> ConnectionService.getInstance().getMaxValidStrikePage(server, userId))
                .orElse(0);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        Optional<Server> serverOptional = getServer();

        serverOptional.ifPresent(server -> {
            List<StrikeData> strikeData = ConnectionService.getInstance().loadValidStrikeData(server.getId(), userId);

            updater.removeAllEmbeds()
                    .addEmbed(ApplicationService.getInstance().formatStrikes(strikeData,
                            BotStarter.getInstance().getBot().getUserById(userId).join(), currentPage));
        });
    }
}