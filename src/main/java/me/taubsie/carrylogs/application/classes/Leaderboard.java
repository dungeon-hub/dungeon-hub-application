package me.taubsie.carrylogs.application.classes;

import java.util.Map;

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
}