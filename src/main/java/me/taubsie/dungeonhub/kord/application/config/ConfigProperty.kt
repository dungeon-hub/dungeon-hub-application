package me.taubsie.dungeonhub.kord.application.config

import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import lombok.Getter
import java.util.*
import java.util.stream.Collectors

@Getter
enum class ConfigProperty(override val readableName: String) : ChoiceEnum {
    DISCORD_BOT_TOKEN("discord-bot.token"),

    //Internal API
    API_URL("api.url"),
    CDN_URL("cdn.public-url"),
    STATIC_URL("cdn.static-url"),

    AUTH_LOGIN_URL("auth.login_url"),
    AUTH_CLIENT_ID("auth.client_id"),
    AUTH_CLIENT_SECRET("auth.client_secret"),

    //External APIs
    HYPIXEL_API_KEY("hypixel-api.key"),

    SKYCRYPT_API_URL("skycrypt-api.url"),

    SAFETY_API_KEY("safety-api.key"),
    SAFETY_API_URL("safety-api.url"),

    JERRY_API_KEY("jerry.key"),
    JERRY_API_URL("jerry.url");

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
