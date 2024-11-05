package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import net.dungeonhub.model.carry_type.CarryTypeCreationModel;
import net.dungeonhub.model.carry_type.CarryTypeModel;
import net.dungeonhub.model.carry_type.CarryTypeUpdateModel;
import net.dungeonhub.service.MoshiService;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CarryTypeConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(CarryTypeConnection.class);
    private static final Map<Long, CarryTypeConnection> instances = new HashMap<>();
    private final long server;

    public CarryTypeConnection(long server) {
        this.server = server;
    }

    public static CarryTypeConnection getInstance(long server) {
        return instances.computeIfAbsent(server, CarryTypeConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + server + "/carry-type";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<CarryTypeModel> getById(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, CarryTypeModel.Companion::fromJson);
    }

    //TODO dedicated endpoint?
    public Optional<CarryTypeModel> getByIdentifier(String identifier) {
        return getAllCarryTypes()
                .flatMap(carryTypeModels -> carryTypeModels
                        .stream().filter(carryTypeModel -> carryTypeModel.getIdentifier().equalsIgnoreCase(identifier))
                        .findFirst());
    }

    public Optional<CarryTypeModel> addNewCarryType(CarryTypeCreationModel creationModel) {
        HttpUrl url = getApiUrl().build();

        RequestBody requestBody = RequestBody.create(
                creationModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .post(requestBody)
                .build();

        return executeRequest(request, CarryTypeModel.Companion::fromJson);
    }

    public Optional<CarryTypeModel> updateCarryType(long id, CarryTypeUpdateModel updateModel) {
        HttpUrl url = getApiUrl(id).build();

        RequestBody requestBody = RequestBody.create(
                updateModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url)
                .put(requestBody)
                .build();

        return executeRequest(request, CarryTypeModel.Companion::fromJson);
    }

    public Optional<CarryTypeModel> deleteCarryType(CarryTypeModel carryTypeModel) {
        HttpUrl url = getApiUrl(carryTypeModel.getId()).build();

        Request request = getApiRequest(url)
                .delete()
                .build();

        return executeRequest(request, CarryTypeModel.Companion::fromJson);
    }

    public Optional<List<CarryTypeModel>> getAllCarryTypes() {
        HttpUrl url = getApiUrl("all").build();

        Request request = getApiRequest(url).get().build();

        return executeRequest(request, MoshiService.INSTANCE.getCarryTypeListMoshi()::fromJson);
    }
}