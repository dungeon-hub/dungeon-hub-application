package me.taubsie.dungeonhub.application.connection.dungeon_hub;

import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.Optional;

public class ContentConnection {
    private static ContentConnection instance;

    public static ContentConnection getInstance() {
        if(instance == null) {
            instance = new ContentConnection();
        }

        return instance;
    }

    private HttpUrl.Builder getApiUrl() {
        return HttpUrl.get(ConfigProperty.API_URL + "cdn")
                .newBuilder();
    }

    private HttpUrl.Builder getApiUrl(String uri) {
        return HttpUrl.get(ConfigProperty.API_URL + "cdn/" + uri)
                .newBuilder();
    }

    private Optional<String> performUpload(byte[] data, HttpUrl url) {
        RequestBody requestBody = RequestBody.create(
                data,
                MediaType.parse("application/octet-stream")
        );

        Request request = DungeonHubConnection.getInstance()
                .getApiRequest(url)
                .post(requestBody)
                .build();

        return DungeonHubConnection.getInstance()
                .executeRequest(request);
    }

    public Optional<String> uploadFile(byte[] data, String fileName) {
        HttpUrl url = getApiUrl(fileName).build();

        return performUpload(data, url);
    }

    public Optional<String> uploadFile(byte[] data) {
        HttpUrl url = getApiUrl().build();

        return performUpload(data, url);
    }
}