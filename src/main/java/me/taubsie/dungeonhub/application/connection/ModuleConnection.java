package me.taubsie.dungeonhub.application.connection;

import com.google.gson.Gson;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.common.DungeonHubService;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Function;

public interface ModuleConnection {
    String getModuleApiPrefix();

    Logger getLogger();

    default Gson getGson() {
        return DungeonHubService.getInstance().getGson();
    }

    //TODO remove?
    default <T> T fromJson(String json, Class<T> clazz) {
        return getGson().fromJson(json, clazz);
    }

    //TODO remove?
    default <T> String toJson(T entity) {
        return getGson().toJson(entity);
    }

    default String getApiPrefix() {
        return "api/v1/";
    }

    default MediaType getJsonMediaType() {
        return MediaType.parse("application/json; charset=utf-8");
    }

    default Request.Builder getApiRequest(String uri) {
        return getApiRequest(getApiUrl(uri).build());
    }

    default Request.Builder getApiRequest(HttpUrl httpUrl) {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        return new Request.Builder()
                .url(httpUrl)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + DungeonHubConnection.getInstance().getApiToken());
    }

    default HttpUrl.Builder getApiUrl() {
        return getApiUrl("");
    }

    default HttpUrl.Builder getApiUrl(Long id) {
        return getApiUrl(String.valueOf(id));
    }

    default HttpUrl.Builder getApiUrl(String uri) {
        String prefix = (getModuleApiPrefix() == null || getModuleApiPrefix().isBlank())
                ? ""
                : getModuleApiPrefix() + "/";

        return HttpUrl.get(ConfigProperty.API_URL + getApiPrefix() + prefix + uri)
                .newBuilder();
    }

    default <T> Optional<T> executeRequest(Request request, Function<String, T> function) {
        return DungeonHubConnection.getInstance().executeRequest(request, function);
    }

    default Optional<String> executeRequest(Request request) {
        return DungeonHubConnection.getInstance().executeRequest(request);
    }
}