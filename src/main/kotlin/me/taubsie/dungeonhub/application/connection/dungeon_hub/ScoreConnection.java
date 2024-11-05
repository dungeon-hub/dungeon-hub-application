package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.enums.ScoreResetType;
import net.dungeonhub.enums.ScoreType;
import net.dungeonhub.model.carry_type.CarryTypeModel;
import net.dungeonhub.model.score.LeaderboardModel;
import net.dungeonhub.model.score.ScoreModel;
import net.dungeonhub.model.score.ScoreResetModel;
import net.dungeonhub.model.score.ScoreUpdateModel;
import net.dungeonhub.service.MoshiService;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScoreConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(ScoreConnection.class);
    private static final Map<CarryTypeModel, ScoreConnection> instances = new HashMap<>();
    private final CarryTypeModel carryTypeModel;

    public ScoreConnection(CarryTypeModel carryTypeModel) {
        this.carryTypeModel = carryTypeModel;
    }

    public static ScoreConnection getInstance(CarryTypeModel carryTypeModel) {
        return instances.computeIfAbsent(carryTypeModel, ScoreConnection::new);
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + carryTypeModel.getServer().getId() + "/carry-type/" + carryTypeModel.getId() + "/score";
    }

    public Optional<ScoreModel> getScore(long id, ScoreType scoreType) {
        HttpUrl url = getApiUrl(id)
                .addQueryParameter("score-type", scoreType.name())
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, ScoreModel.Companion::fromJson);
    }

    public Optional<List<ScoreModel>> getScores() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getScoreListMoshi()::fromJson);
    }

    public Optional<List<ScoreModel>> getScores(long id) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("id", String.valueOf(id))
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getScoreListMoshi()::fromJson);
    }

    public Optional<ScoreModel> getScore(long id) {
        return getScore(id, ScoreType.Default);
    }

    public Optional<List<ScoreModel>> updateScores(ScoreUpdateModel scoreUpdateModel) {
        HttpUrl url = getApiUrl().build();

        RequestBody requestBody = RequestBody.create(
                scoreUpdateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getScoreListMoshi()::fromJson);
    }

    public Optional<LeaderboardModel> loadLeaderboard(@Nullable ScoreType scoreType, @Nullable Integer page) {
        return loadLeaderboard(scoreType, page, null);
    }

    public Optional<LeaderboardModel> loadLeaderboard(@Nullable ScoreType scoreType, @Nullable Integer page,
                                                      @Nullable Long userId) {
        if (scoreType == null) {
            scoreType = ScoreType.Default;
        }

        if (page == null || page < 0) {
            page = 0;
        }

        HttpUrl.Builder urlBuilder = getApiUrl("leaderboard")
                .addQueryParameter("score-type", scoreType.name())
                .addQueryParameter("page", String.valueOf(page));

        if (userId != null) {
            urlBuilder.addQueryParameter("user", String.valueOf(userId));
        }

        Request request = getApiRequest(urlBuilder.build())
                .get()
                .build();

        return executeRequest(request, LeaderboardModel.Companion::fromJson);
    }

    public Optional<ScoreResetModel> resetScore(ScoreResetType scoreResetType) {
        HttpUrl url = getApiUrl().addQueryParameter("score-type", scoreResetType.name()).build();

        Request request = getApiRequest(url).delete().build();

        return executeRequest(request, ScoreResetModel.Companion::fromJson);
    }
}