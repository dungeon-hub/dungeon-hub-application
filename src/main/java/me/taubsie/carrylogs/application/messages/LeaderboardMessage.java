package me.taubsie.carrylogs.application.messages;

import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.CarryType;
import me.taubsie.dungeonhub.common.LeaderboardType;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;

public class LeaderboardMessage extends PageableMessage {
    private final CarryType carryType;
    private final LeaderboardType leaderboardType;

    public LeaderboardMessage(int currentPage, long channel, long message, CarryType carryType, LeaderboardType leaderboardType) {
        super(currentPage, channel, message);
        this.carryType = carryType;
        this.leaderboardType = leaderboardType;
    }

    @Override
    public int getMaxPage() {
        return DungeonHubConnection.getInstance().getMaxLeaderboardPage(carryType, leaderboardType);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        updater.removeAllEmbeds()
                .addEmbed(
                        LeaderboardService.getInstance().getLeaderboardEmbed(
                                LeaderboardService.getInstance().getLeaderboardTitle(carryType, leaderboardType),
                                DungeonHubConnection.getInstance().getLeaderboardData(carryType, leaderboardType, currentPage),
                                currentPage,
                                getMaxPage()
                        )
                ).update();
    }
}