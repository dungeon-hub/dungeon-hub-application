package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.common.model.server.ServerModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class ServerConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(ServerConnection.class);
    private static ServerConnection instance;

    public static ServerConnection getInstance() {
        if (instance == null) {
            instance = new ServerConnection();
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

    public Optional<ServerModel> findServerById(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, ServerModel::fromJson);
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

    public Optional<List<ServerModel>> loadAllServers() {
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

    public Optional<List<ScoreModel>> getScores(ServerModel serverModel, long id) {
        HttpUrl url = getApiUrl(serverModel.getId() + "/score/" + id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> DungeonHubService.getInstance()
                .getGson().fromJson(s, DungeonHubService.getInstance().getScoreModelListType()));
    }
}