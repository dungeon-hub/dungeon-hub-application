package me.taubsie.dungeonhub.application.messages;

import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;
import org.jetbrains.annotations.Nullable;

public class LeaderboardMessage extends PageableMessage {
    @Nullable
    private final CarryTypeModel carryType;
    private final ScoreType scoreType;
    private final Long userId;

    public LeaderboardMessage(int currentPage, long channel, long message, @Nullable CarryTypeModel carryType,
                              ScoreType scoreType, Long userId) {
        super(currentPage, channel, message);
        this.carryType = carryType;
        this.scoreType = scoreType;
        this.userId = userId;
    }

    @Override
    public int getMaxPage() {
        return ScoreConnection.getInstance(carryType)
                .loadLeaderboard(scoreType, 0, userId)
                .map(LeaderboardModel::getTotalPages)
                .orElse(0);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        String leaderboardTitle = LeaderboardService.getInstance().getLeaderboardTitle(carryType, scoreType);

        //TODO fix
        /*updater.removeAllEmbeds()
                .addEmbed(
                        Optional.ofNullable(carryType)
                                .map(carryTypeModel -> ScoreConnection.getInstance(carryType)
                                        .loadLeaderboard(scoreType, currentPage, userId))
                                .orElseGet(() -> DiscordServerConnection.getInstance()
                                        .loadTotalLeaderboard(getServer().orElseThrow().getId(), scoreType, currentPage, userId))
                                .map(model -> LeaderboardService.getInstance()
                                        .getLeaderboardEmbed(leaderboardTitle, model))
                                .orElseGet(() -> LeaderboardService.getInstance()
                                        .getEmptyLeaderboardEmbed(leaderboardTitle))
                ).update();*/
    }
}