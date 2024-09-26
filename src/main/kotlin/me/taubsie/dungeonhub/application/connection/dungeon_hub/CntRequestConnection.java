package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.connection.ModuleConnection;
import me.taubsie.dungeonhub.common.model.cnt_request.CntRequestModel;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CntRequestConnection implements ModuleConnection {
    private static final Logger logger = LoggerFactory.getLogger(CntRequestConnection.class);
    private static final Map<Long, CntRequestConnection> instances = new HashMap<>();
    private final long server;

    public CntRequestConnection(long server) {
        this.server = server;
    }

    public static CntRequestConnection getInstance(long server) {
        return instances.computeIfAbsent(server, CntRequestConnection::new);
    }

    @Override
    public String getModuleApiPrefix() {
        return "server/" + server + "/cnt-request";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public Optional<CntRequestModel> findCntRequest(long messageId) {
        HttpUrl url = getApiUrl("find")
                .addQueryParameter("message-id", String.valueOf(messageId))
                .build();

        Request request = getApiRequest(url)
                .get()
                .build();

        return executeRequest(request, s -> CntRequestModel::fromJson);
    }
}