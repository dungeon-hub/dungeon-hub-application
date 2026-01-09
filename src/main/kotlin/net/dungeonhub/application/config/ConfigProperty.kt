package net.dungeonhub.application.config

import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.i18n.toKey
import dev.kordex.i18n.Key
import java.util.*
import java.util.stream.Collectors

enum class ConfigProperty(override val readableName: Key) : ChoiceEnum {
    DISCORD_BOT_TOKEN("discord-bot.token"),
    HYPIXEL_CACHE_TYPE("hypixel-wrapper.cache-type"),

    //Database
    MONGODB_CONNECTION_STRING("mongodb.connection-string"),
    MONGODB_DATABASE_NAME("mongodb.database-name"),

    //Internal API
    API_URL("api.url"),
    CDN_URL("cdn.public-url"),
    STATIC_URL("cdn.static-url"),

    AUTH_LOGIN_URL("auth.login_url"),
    AUTH_CLIENT_ID("auth.client_id"),
    AUTH_CLIENT_SECRET("auth.client_secret"),

    //External APIs
    HYPIXEL_API_KEY("hypixel-api.key"),

    SAFETY_API_KEY("safety-api.key"),
    SAFETY_API_URL("safety-api.url"),

    JERRY_API_KEY("jerry.key"),
    JERRY_API_URL("jerry.url"),

    BLOCK_GAME_API_URL("blockgame.url"),
    BLOCK_GAME_API_KEY("blockgame.key");

    constructor(readableName: String) : this(readableName.toKey())

    var value: String?
        get() = ConfigService.getConfig(this)
        set(value) {
            ConfigService.setConfig(this, value)
        }

    override fun toString(): String {
        return value!!
    }

    companion object {
        val properties: Set<ConfigProperty>
            get() = Arrays.stream(entries.toTypedArray())
                .collect(Collectors.toSet())
    }
}
