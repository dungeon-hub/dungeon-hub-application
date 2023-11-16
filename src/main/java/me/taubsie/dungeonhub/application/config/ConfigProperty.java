package me.taubsie.dungeonhub.application.config;

import lombok.Getter;
import me.taubsie.dungeonhub.common.Nameable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum ConfigProperty implements Nameable {
    API_URL("api.url"),
    API_USER("api.user"),
    API_PASSWORD("api.password"),
    CDN_URL("cdn.public-url"),
    DISCORD_BOT_TOKEN("discord-bot.token"),
    SAFETY_API_KEY("safety-api.key"),
    SAFETY_API_URL("safety-api.url"),
    HYPIXEL_API_KEY("hypixel-api.key");

    final String name;

    ConfigProperty(String name) {
        this.name = name;
    }

    public String getValue() {
        return ConfigService.getInstance().getConfig(this);
    }

    //Dumb warning, this doesn't set the value of any enum fields, it just refers to the ConfigService.
    @SuppressWarnings("java:S3066")
    public void setValue(@NotNull String value) {
        ConfigService.getInstance().setConfig(this, value);
    }

    @Override
    public String toString() {
        return getValue();
    }

    public static Set<ConfigProperty> getProperties() {
        return Arrays.stream(ConfigProperty.values())
                .collect(Collectors.toSet());
    }
}
