package me.taubsie.dungeonhub.application.classes;

import me.taubsie.dungeonhub.application.service.LeaderboardService;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.Map;

/**
 * This class holds the data for a simple leaderboard without pages.
 * It contains the title of the leaderboard and the score data, to allow easy querying and building.
 */
public class Leaderboard {
    private final String title;
    private final Map<Long, Long> scoreData;

    public Leaderboard(String title, Map<Long, Long> scoreData) {
        this.title = title;
        this.scoreData = scoreData;
    }

    public String getLeaderboardTitle() {
        return title;
    }

    public Map<Long, Long> getScoreData() {
        return scoreData;
    }

    public EmbedBuilder getEmbed() {
        return LeaderboardService.getInstance().getLeaderboardEmbed(title, scoreData, 1);
    }
}