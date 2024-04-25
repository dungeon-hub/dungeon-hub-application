package me.taubsie.dungeonhub.application.classes;

import lombok.Getter;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

/**
 * This class holds the data for a simple leaderboard without pages.
 * It contains the title of the leaderboard and the score data, to allow easy querying and building.
 */
public class Leaderboard {
    private final String title;
    @Getter
    private final LeaderboardModel leaderboardModel;

    public Leaderboard(String title, LeaderboardModel leaderboardModel) {
        this.title = title;
        this.leaderboardModel = leaderboardModel;
    }

    public String getLeaderboardTitle() {
        return title;
    }

    public boolean isEmpty() {
        return leaderboardModel == null || leaderboardModel.getScores().isEmpty();
    }

    public EmbedBuilder getEmbed() {
        if(isEmpty()) {
            return LeaderboardService.getInstance().getEmptyLeaderboardEmbed(title);
        }

        return LeaderboardService.getInstance().getLeaderboardEmbed(title, leaderboardModel);
    }
}