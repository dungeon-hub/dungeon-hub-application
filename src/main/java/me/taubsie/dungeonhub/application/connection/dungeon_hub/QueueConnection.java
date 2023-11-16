package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.enums.QueueStep;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueCreationModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueUpdateModel;
import me.taubsie.dungeonhub.common.model.score.LoggedCarryModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

public class QueueConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(QueueConnection.class);
    private static QueueConnection instance;

    public static QueueConnection getInstance() {
        if (instance == null) {
            instance = new QueueConnection();
        }

        return instance;
    }

    public Optional<CarryQueueModel> addNewQueue(CarryDifficultyModel carryDifficultyModel,
                                                 CarryQueueCreationModel creationModel) {
        HttpUrl url = getApiUrl("carry-difficulty/" + carryDifficultyModel.getId())
                .build();

        RequestBody requestBody = RequestBody.create(
                creationModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .post(requestBody)
                .build();

        return executeRequest(request, CarryQueueModel::fromJson);
    }

    public Optional<Set<CarryQueueModel>> getCarryQueueByRelatedIdAndQueueStep(@NotNull Long relatedId,
                                                                               @NotNull QueueStep queueStep) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("related-id", String.valueOf(relatedId))
                .addQueryParameter("queue-step", queueStep.name())
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> DungeonHubService.getInstance()
                .getGson()
                .fromJson(s, DungeonHubService.getInstance()
                        .getCarryQueueModelSetType()));
    }

    public Optional<Set<CarryQueueModel>> getCarryQueueByRelatedId(@NotNull Long id) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("related-id", String.valueOf(id))
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> DungeonHubService.getInstance()
                .getGson()
                .fromJson(s, DungeonHubService.getInstance()
                        .getCarryQueueModelSetType()));
    }

    public Optional<Set<CarryQueueModel>> getCarryQueuesByQueueStep(@NotNull QueueStep step) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("queue-step", step.name())
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> DungeonHubService.getInstance()
                .getGson()
                .fromJson(s, DungeonHubService.getInstance()
                        .getCarryQueueModelSetType()));
    }

    public Optional<CarryQueueModel> updateQueue(Long id, CarryQueueUpdateModel updateModel) {
        HttpUrl url = getApiUrl(id)
                .build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, CarryQueueModel::fromJson);
    }

    public void deleteQueue(Long id) {
        HttpUrl url = getApiUrl(id)
                .build();

        Request request = getApiRequest(url)
                .delete()
                .build();

        executeRequest(request);
    }

    public Optional<LoggedCarryModel> logQueue(Long id, CarryQueueUpdateModel updateModel) {
        HttpUrl url = getApiUrl("log/" + id)
                .build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .post(requestBody)
                .build();

        return executeRequest(request, LoggedCarryModel::fromJson);
    }

    @Override
    public String getModuleApiPrefix() {
        return "queue";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}