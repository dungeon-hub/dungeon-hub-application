package me.taubsie.dungeonhub.application.messages;

import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.CarryType;
import me.taubsie.dungeonhub.common.ScoreType;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;

public class LeaderboardMessage extends PageableMessage {
    private final CarryType carryType;
    private final ScoreType scoreType;

    public LeaderboardMessage(int currentPage, long channel, long message, CarryType carryType, ScoreType scoreType) {
        super(currentPage, channel, message);
        this.carryType = carryType;
        this.scoreType = scoreType;
    }

    @Override
    public int getMaxPage() {
        return DungeonHubConnection.getInstance().getMaxLeaderboardPage(carryType, scoreType);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        updater.removeAllEmbeds()
                .addEmbed(
                        LeaderboardService.getInstance().getLeaderboardEmbed(
                                LeaderboardService.getInstance().getLeaderboardTitle(carryType, scoreType),
                                DungeonHubConnection.getInstance().getLeaderboardData(carryType, scoreType, currentPage),
                                currentPage,
                                getMaxPage()
                        )
                ).update();
    }
}