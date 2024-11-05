package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.enums.QueueStep;
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel;
import net.dungeonhub.model.carry_queue.CarryQueueCreationModel;
import net.dungeonhub.model.carry_queue.CarryQueueModel;
import net.dungeonhub.model.carry_queue.CarryQueueUpdateModel;
import net.dungeonhub.model.score.LoggedCarryModel;
import net.dungeonhub.service.MoshiService;
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

        return executeRequest(request, CarryQueueModel.Companion::fromJson);
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

        return executeRequest(request, MoshiService.INSTANCE.getCarryQueueSetMoshi()::fromJson);
    }

    public Optional<Set<CarryQueueModel>> getCarryQueueByRelatedId(@NotNull Long id) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("related-id", String.valueOf(id))
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getCarryQueueSetMoshi()::fromJson);
    }

    public Optional<Set<CarryQueueModel>> getCarryQueuesByQueueStep(@NotNull QueueStep step) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("queue-step", step.name())
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getCarryQueueSetMoshi()::fromJson);
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

        return executeRequest(request, CarryQueueModel.Companion::fromJson);
    }

    public boolean deleteQueue(Long id) {
        HttpUrl url = getApiUrl(id)
                .build();

        Request request = getApiRequest(url)
                .delete()
                .build();

        return executeRequest(request).isPresent();
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

        return executeRequest(request, LoggedCarryModel.Companion::fromJson);
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