package me.taubsie.dungeonhub.application.connection;

import me.taubsie.dungeonhub.kord.application.config.ConfigProperty;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;

public interface ModuleConnection extends Connection {
    String getModuleApiPrefix();

    default Request.Builder getApiRequest(String uri) {
        return getApiRequest(getApiUrl(uri).build());
    }

    default Request.Builder getApiRequest(HttpUrl httpUrl) {
        MediaType mediaType = MediaType.get("multipart/form-data; boundary=---011000010111000001101001");

        return new Request.Builder()
                .url(httpUrl)
                .addHeader("Content-Type", mediaType.toString())
                .addHeader("Authorization", "Bearer " + AuthorizationConnection.getInstance().getApiToken());
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
                : getModuleApiPrefix() + (uri == null || uri.isBlank() ? "" : "/");

        return HttpUrl.get(ConfigProperty.API_URL + getApiPrefix() + prefix + uri)
                .newBuilder();
    }

    default String getApiPrefix() {
        return "api/v1/";
    }
}