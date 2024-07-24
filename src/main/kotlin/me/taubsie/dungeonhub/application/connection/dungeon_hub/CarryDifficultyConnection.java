package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyUpdateModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CarryDifficultyConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(CarryDifficultyConnection.class);
    private static final Map<CarryTierModel, CarryDifficultyConnection> instances = new HashMap<>();
    private final CarryTierModel carryTierModel;

    public CarryDifficultyConnection(CarryTierModel carryTierModel) {
        this.carryTierModel = carryTierModel;
    }

    public static CarryDifficultyConnection getInstance(CarryTierModel carryTierModel) {
        return instances.computeIfAbsent(carryTierModel, CarryDifficultyConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/"
                + carryTierModel.getCarryType().getServer().getId()
                + "/carry-type/"
                + carryTierModel.getCarryType().getId()
                + "/carry-tier/"
                + carryTierModel.getId()
                + "/carry-difficulty";
    }

    public Optional<CarryDifficultyModel> getCarryDifficulty(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, CarryDifficultyModel::fromJson);
    }

    public Optional<List<CarryDifficultyModel>> getAllCarryDifficulties() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> getGson().fromJson(s,
                DungeonHubService.getInstance().getCarryDifficultyListType()));
    }

    public Optional<CarryDifficultyModel> getByIdentifier(String identifier) {
        return getAllCarryDifficulties()
                .flatMap(carryDifficultyModels -> carryDifficultyModels
                        .stream().filter(carryDifficultyModel -> carryDifficultyModel.getIdentifier().equalsIgnoreCase(identifier))
                        .findFirst());
    }

    public Optional<CarryDifficultyModel> updateCarryDifficulty(long id, CarryDifficultyUpdateModel updateModel) {
        HttpUrl url = getApiUrl(id).build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, CarryDifficultyModel::fromJson);
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}