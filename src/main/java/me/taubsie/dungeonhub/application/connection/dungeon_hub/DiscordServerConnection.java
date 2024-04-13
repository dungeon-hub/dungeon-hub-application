package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.common.model.server.DiscordServerModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class DiscordServerConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(DiscordServerConnection.class);
    private static DiscordServerConnection instance;

    public static DiscordServerConnection getInstance() {
        if (instance == null) {
            instance = new DiscordServerConnection();
        }

        return instance;
    }

    @Override
    public String getModuleApiPrefix() {
        return "server";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<DiscordServerModel> findServerById(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, DiscordServerModel::fromJson);
    }

    public Optional<List<CarryTierModel>> getAllCarryTiers(long serverId) {
        HttpUrl url = getApiUrl(serverId + "/carry-tiers").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> getGson().fromJson(s,
                DungeonHubService.getInstance().getCarryTierListType()));
    }

    public Optional<List<CarryDifficultyModel>> getAllCarryDifficulties(long serverId) {
        HttpUrl url = getApiUrl(serverId + "/carry-difficulties").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> getGson().fromJson(s,
                DungeonHubService.getInstance().getCarryDifficultyListType()));
    }

    public Optional<List<DiscordServerModel>> loadAllServers() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s ->
                DungeonHubService.getInstance().getGson().fromJson(s,
                        DungeonHubService.getInstance().getServerModelListType()));
    }

    public Optional<CarryTierModel> getCarryTierFromCategory(long serverId, long categoryId) {
        HttpUrl url = getApiUrl(serverId + "/category/" + categoryId + "/carry-tier").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, CarryTierModel::fromJson);
    }

    public Optional<List<ScoreModel>> getScores(DiscordServerModel serverModel, long id) {
        HttpUrl url = getApiUrl(serverModel.getId() + "/score/" + id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> DungeonHubService.getInstance()
                .getGson().fromJson(s, DungeonHubService.getInstance().getScoreModelListType()));
    }

    public Optional<LeaderboardModel> loadTotalLeaderboard(long serverId, @Nullable ScoreType scoreType, @Nullable Integer page,
                                                      @Nullable Long userId) {
        if (scoreType == null) {
            scoreType = ScoreType.DEFAULT;
        }

        if (page == null || page < 0) {
            page = 0;
        }

        HttpUrl.Builder urlBuilder = getApiUrl(serverId + "/total-leaderboard")
                .addQueryParameter("score-type", scoreType.name())
                .addQueryParameter("page", String.valueOf(page));

        if (userId != null) {
            urlBuilder.addQueryParameter("user", String.valueOf(userId));
        }

        Request request = getApiRequest(urlBuilder.build())
                .get()
                .build();

        return executeRequest(request, LeaderboardModel::fromJson);
    }
}