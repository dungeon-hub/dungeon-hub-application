package me.taubsie.dungeonhub.application.messages;

import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.start.BotStarter;
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
                .map(server -> DungeonHubConnection.getInstance().getMaxValidStrikePage(server, userId))
                .orElse(0);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        Optional<Server> serverOptional = getServer();

        serverOptional.ifPresent(server -> {
            List<StrikeData> strikeData = DungeonHubConnection.getInstance().loadValidStrikeData(server.getId(), userId);

            updater.removeAllEmbeds()
                    .addEmbed(ApplicationService.getInstance().formatStrikes(strikeData,
                            BotStarter.getInstance().getBot().getUserById(userId).join(), currentPage));
        });
    }
}