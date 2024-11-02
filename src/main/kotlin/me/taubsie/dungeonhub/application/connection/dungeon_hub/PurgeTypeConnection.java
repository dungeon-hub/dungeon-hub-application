package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.model.carry_type.CarryTypeModel;
import net.dungeonhub.model.purge_type.PurgeTypeModel;
import net.dungeonhub.service.MoshiService;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PurgeTypeConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(PurgeTypeConnection.class);
    private static final Map<CarryTypeModel, PurgeTypeConnection> instances = new HashMap<>();
    private final CarryTypeModel carryTypeModel;

    public PurgeTypeConnection(CarryTypeModel carryTypeModel) {
        this.carryTypeModel = carryTypeModel;
    }

    public static PurgeTypeConnection getInstance(CarryTypeModel carryTypeModel) {
        return instances.computeIfAbsent(carryTypeModel, PurgeTypeConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + carryTypeModel.getServer().getId() + "/carry-type/" + carryTypeModel.getId() + "/purge-type";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    //TODO own endpoint
    public Optional<PurgeTypeModel> getByIdentifier(String identifier) {
        return getAllPurgeTypes()
                .flatMap(purgeTypeModels -> purgeTypeModels
                        .stream().filter(carryTypeModel -> carryTypeModel.getIdentifier().equalsIgnoreCase(identifier))
                        .findFirst());
    }

    public Optional<List<PurgeTypeModel>> getAllPurgeTypes() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, MoshiService.INSTANCE.getPurgeTypeListMoshi()::fromJson);
    }
}