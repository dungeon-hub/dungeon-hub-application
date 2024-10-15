package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierCreationModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierUpdateModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CarryTierConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(CarryTierConnection.class);
    private static final Map<CarryTypeModel, CarryTierConnection> instances = new HashMap<>();
    private final CarryTypeModel carryTypeModel;

    public CarryTierConnection(CarryTypeModel carryTypeModel) {
        this.carryTypeModel = carryTypeModel;
    }

    public static CarryTierConnection getInstance(CarryTypeModel carryTypeModel) {
        return instances.computeIfAbsent(carryTypeModel, CarryTierConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + carryTypeModel.getServer().getId() + "/carry-type/" + carryTypeModel.getId() + "/carry-tier";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<CarryTierModel> getByIdentifier(String identifier) {
        return getAllCarryTiers()
                .flatMap(carryTierModels -> carryTierModels
                        .stream().filter(carryTierModel -> carryTierModel.getIdentifier().equalsIgnoreCase(identifier))
                        .findFirst());
    }

    /**
     * Loads all available carry tiers for the given carry type.
     * This represents the tiers of carry, so for example floor 1, master mode floor 1, tier 4, kuudra, ...
     *
     * @return The list of carry tiers that were loaded from the database.
     */
    public Optional<List<CarryTierModel>> getAllCarryTiers() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> getGson().fromJson(s,
                DungeonHubService.getInstance().getCarryTierListType()));
    }

    public Optional<CarryTierModel> createCarryTier(CarryTierCreationModel creationModel) {
        HttpUrl url = getApiUrl().build();

        RequestBody requestBody = RequestBody.create(
                creationModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .post(requestBody)
                .build();

        return executeRequest(request, CarryTierModel::fromJson);
    }

    public Optional<CarryTierModel> updateCarryTier(long id, CarryTierUpdateModel updateModel) {
        HttpUrl url = getApiUrl(id).build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, CarryTierModel::fromJson);
    }

    public Optional<CarryTierModel> deleteCarryTier(CarryTierModel carryTierModel) {
        HttpUrl url = getApiUrl(carryTierModel.getId()).build();

        Request request = getApiRequest(url)
                .delete()
                .build();

        return executeRequest(request, CarryTierModel::fromJson);
    }
}