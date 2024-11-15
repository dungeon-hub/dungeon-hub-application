package me.taubsie.dungeonhub.application.connection;

import com.google.gson.Gson;
import net.dungeonhub.service.GsonService;
import okhttp3.MediaType;
import okhttp3.Request;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.Optional;

public interface Connection {
    Logger getLogger();

    default Gson getGson() {
        //noinspection deprecation
        return GsonService.INSTANCE.getGson();
    }

    default <T> T fromJson(String json, Class<T> clazz) {
        return getGson().fromJson(json, clazz);
    }

    default <T> T fromJson(String json, Type typeOfT) {
        return getGson().fromJson(json, typeOfT);
    }

    default <T> String toJson(T entity) {
        return getGson().toJson(entity);
    }

    default MediaType getJsonMediaType() {
        return MediaType.parse("application/json; charset=utf-8");
    }

    default <T> Optional<T> executeRequest(Request request, MappingFunction<String, T> function) {
        return DungeonHubConnection.getInstance().executeRequest(request, function);
    }

    default Optional<String> executeRequest(Request request) {
        return DungeonHubConnection.getInstance().executeRequest(request);
    }
}