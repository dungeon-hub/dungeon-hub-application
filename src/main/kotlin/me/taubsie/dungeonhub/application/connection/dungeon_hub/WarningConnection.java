package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.model.warning.*;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WarningConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(WarningConnection.class);
    private static final Map<Long, WarningConnection> instances = new HashMap<>();
    private final long serverId;

    public WarningConnection(long serverId) {
        this.serverId = serverId;
    }

    public static WarningConnection getInstance(long serverId) {
        return instances.computeIfAbsent(serverId, WarningConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + serverId + "/warns";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<List<DetailedWarningModel>> getAllWarns(long userId) {
        HttpUrl url = getApiUrl("all")
                .addQueryParameter("user", String.valueOf(userId))
                .build();

        Request request = getApiRequest(url).get().build();

        return executeRequest(request, s -> fromJson(s, DungeonHubService.getInstance().getDetailedWarningModelListType()));
    }

    public Optional<List<DetailedWarningModel>> getActiveWarns(long userId) {
        HttpUrl url = getApiUrl("active")
                .addQueryParameter("user", String.valueOf(userId))
                .build();

        Request request = getApiRequest(url).get().build();

        return executeRequest(request, s -> fromJson(s, DungeonHubService.getInstance().getDetailedWarningModelListType()));
    }

    public Optional<AddedWarningModel> addWarning(WarningCreationModel creationModel) {
        HttpUrl url = getApiUrl().build();

        RequestBody requestBody = RequestBody.create(
                creationModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url).post(requestBody).build();

        return executeRequest(request, AddedWarningModel::fromJson);
    }

    public Optional<DetailedWarningModel> deactivateWarning(long id) {
        HttpUrl url = getApiUrl(id).build();

        Request request = getApiRequest(url).delete().build();

        return executeRequest(request, DetailedWarningModel::fromJson);
    }

    public Optional<DetailedWarningModel> addEvidence(long warningId, WarningEvidenceCreationModel evidenceCreationModel) {
        HttpUrl url = getApiUrl(warningId + "/evidence").build();

        RequestBody requestBody = RequestBody.create(
                evidenceCreationModel.toJson(),
                getJsonMediaType()
        );

        Request request = getApiRequest(url).put(requestBody).build();

        return executeRequest(request, DetailedWarningModel::fromJson);
    }
}