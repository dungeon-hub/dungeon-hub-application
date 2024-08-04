package me.taubsie.dungeonhub.application.connection

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.gateway.builder.PresenceBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.listener.ServerJoinListener
import me.taubsie.dungeonhub.application.loader.ClassLoader
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartPriority
import me.taubsie.dungeonhub.application.loader.StartupListener
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.common.DungeonHubService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.sql.Time
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern
import kotlin.time.toKotlinDuration

/**
 * This is the main-class for the application.
 * It automatically loads all listeners and commands and initiates the bot-instance.
 * Even if it doesn't make much of a difference, it is advised to not use {@link #getBot()} too much.
 *
 * @author Taubsie
 * @since 1.0.0
 */
@OnStart(priority = StartPriority.DISCORD_BOT)
object DiscordConnection : StartupListener {
    private val logger: Logger = LoggerFactory.getLogger(DiscordConnection::class.java)
    private var uptime: Instant = Instant.now()
    private var currentAppearance = 0
    private val possibleAppearances: List<Pair<AppearanceType, suspend () -> String>> = listOf(
        AppearanceType.Custom to {
            "Handling ${
                DiscordUserConnection.getInstance()
                    .countLinkedUsers().orElse("0")
            } linked users!"
        },
        AppearanceType.Watching to {
            "${bot?.kordRef?.totalUserCount() ?: 0} carriers on ${bot?.kordRef?.guilds?.count() ?: 0} servers"
        },
        AppearanceType.Competing to {
            "score leaderboards for first place"
        },
        //TODO uncomment once released
        /*AppearanceType.Custom to {
            "Customize me at dungeon-hub.net"
        },*/
        AppearanceType.Custom to {
            "Running 100% in Kotlin!"
        },
        AppearanceType.Watching to {
            "you clear dungeons"
        },
        AppearanceType.Custom to {
            "Helping you level up!"
        },
        AppearanceType.Playing to {
            "some Master Mode."
        },
        AppearanceType.Custom to {
            "Check out /help for more!"
        },
        AppearanceType.Custom to {
            "Remember to close and /log"
        },
        AppearanceType.Listening to {
            val time = Duration.between(uptime, Instant.now()).toKotlinDuration().toString()

            "discord events since $time"
        }
    )

    enum class AppearanceType(val apply: (text: String) -> (PresenceBuilder.() -> Unit)) {
        /**
         * Playing {text}
         */
        Playing({ s -> { playing(s) } }),

        /**
         * Listening to {text}
         */
        Listening({ s -> { listening(s) } }),

        /**
         * Watching {text}
         */
        Watching({ s -> { watching(s) } }),

        /**
         * Competing in {text}
         */
        Competing({ s -> { competing(s) } }),

        /**
         * {text}
         */
        Custom({ s -> { state = s } });
    }

    var bot: ExtensibleBot? = null

    /**
     * Returns a line for command-line output.
     *
     * @return a line for command-line output.
     */
    private const val LINE = "----------------------------------------"

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            launch {
                ClassLoader.loadStartupListeners()
                ClassLoader.executePreStart()
            }
        }
    }

    /**
     * Method by the {@link StartupListener} interface, this is automatically executed on program launch.
     * This implementation starts the discord-bot.
     */
    override suspend fun preStart() {
        bot = ExtensibleBot(ConfigProperty.DISCORD_BOT_TOKEN.value!!) {
            errorResponse { message, type ->
                embeds = if (type.error is CommandExecutionException) {
                    mutableListOf(ApplicationService.getErrorEmbed(type.error as CommandExecutionException))
                } else {
                    val embed = ApplicationService.getErrorEmbed(CommandExecutionException(type.error))
                    if (message.isNotBlank()) {
                        embed.title = message
                    }

                    mutableListOf(embed)
                }
            }

            hooks {
                beforeStart {
                    ClassLoader.executeStartup()
                }
            }

            @OptIn(PrivilegedIntent::class)
            intents {
                +Intent.GuildMembers
                +Intent.MessageContent
            }

            presence {
                state = "Loading..."
                status = PresenceStatus.Idle
            }
        }

        ClassLoader.loadExtensions(bot!!)

        bot?.on<ReadyEvent> {
            ClassLoader.executePostStart()

            uptime = Instant.now()
        }

        bot?.start()
    }

    /**
     * Returns the formatted message to list all servers the bot is on.
     *
     * @return the formatted message to list all servers the bot is on.
     */
    private suspend fun getServerListMessage(): List<String> {
        val message: MutableList<String> = mutableListOf()

        message.add("Im on servers:")
        message.addAll(
            bot?.kordRef?.guilds?.map { server ->
                "${server.name} with id '${server.id}' by ${server.getOwnerOrNull()?.effectiveName ?: "no-name"} (${server.ownerId})"
            }!!.toList()
        )

        return message
    }

    /**
     * This resets the bot's appearance.
     */
    private suspend fun resetBotAppearance() {
        bot?.kordRef?.editPresence {
            status = PresenceStatus.Online

            currentAppearance = if (currentAppearance >= possibleAppearances.size - 1) 0 else currentAppearance + 1

            val appearance = possibleAppearances[currentAppearance]

            appearance.first.apply(appearance.second())()
        }
    }

    override suspend fun onStart() {
        logger.info(LINE)
        getServerListMessage().forEach(logger::info)
        logger.info(LINE)

        ServerJoinListener.GUILD_ON_JOIN.addAll(
            bot?.kordRef?.guilds?.map { guild ->
                guild.id.value
            }?.toList()!!
        )
    }

    override suspend fun postStart() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runBlocking {
                    resetBotAppearance()
                }
            }
        }, Time(System.currentTimeMillis() + 1000), 1000 * 60 * 2)
    }
}

fun User.getMutualServers(): Flow<Member> {
    return kord.guilds.mapNotNull { server ->
        this.asMemberOrNull(server.id)
    }
}

fun Guild.isDungeonHub(): Boolean {
    return listOf(693263712626278553, 1023684107877761196).contains(id.value.toLong())
}

fun Snowflake.isDungeonHub(): Boolean {
    return listOf(693263712626278553, 1023684107877761196).contains(value.toLong())
}

fun EmbedBuilder.copy(other: EmbedBuilder) {
    this.description = other.description
    this.title = other.title
    this.footer = other.footer
    this.fields = other.fields
    this.color = other.color
    this.timestamp = other.timestamp
    this.url = other.url
    this.image = other.image
    this.author = other.author
    this.thumbnail = other.thumbnail
}

private val messageLinkPattern =
    Pattern.compile("(?x)                               # enable comment mode \n(?i)                             # ignore case \n(?:https?+://)?+                 # 'https://' or 'http://' or '' \n(?:(?:canary|ptb)\\.)?+          # 'canary.' or 'ptb.'\ndiscord(?:app)?+\\.com/channels/ # 'discord(app).com/channels/' \n(?:(?<server>[0-9]++)|@me)       # '@me' or the server id as named group \n/                                # '/' \n(?<channel>[0-9]++)              # the textchannel id as named group \n/                                # '/' \n(?<message>[0-9]++)              # the message id as named group \n")

suspend fun Kord.loadMessageByLink(messageLink: String): Message? {
    val matcher = messageLinkPattern.matcher(messageLink)

    require(matcher.matches()) { "The message link has an invalid format" }

    return getChannel(Snowflake(matcher.group("channel")))
        ?.asChannelOfOrNull<MessageChannel>()
        ?.getMessage(Snowflake(matcher.group("message")))
}

suspend fun Kord.totalUserCount(): Int? {
    return guilds.map { it.memberCount }.reduce { first, second ->
        if (first == null && second == null) {
            return@reduce null
        }

        if (first == null) {
            return@reduce second
        }

        return@reduce first + (second ?: 0)
    }
}

fun EmbedBuilder.setFields(value: JsonElement) {
    value.asJsonArray.asList().stream()
        .map { obj: JsonElement -> obj.asJsonObject }
        .forEach { jsonObject: JsonObject ->
            field(
                jsonObject.getAsJsonPrimitive("name").asString,
                jsonObject.getAsJsonPrimitive("inline")?.asBoolean ?: false
            ) {
                jsonObject.getAsJsonPrimitive("value").asString
            }
        }
}

fun EmbedBuilder.setFooter(value: JsonElement) {
    val footer = value.asJsonObject

    footer {
        text = footer.getAsJsonPrimitive("text").asString
        icon = footer.getAsJsonPrimitive("icon")?.asString
    }
}

fun EmbedBuilder.setThumbnail(value: JsonElement) {
    thumbnail {
        url = value.asJsonObject.getAsJsonPrimitive("url").asString
    }
}

fun EmbedBuilder.setAuthor(value: JsonElement) {
    if (value.isJsonPrimitive) {
        author {
            name = value.asString
        }
    } else {
        val jsonObject = value.asJsonObject

        author {
            name = jsonObject.getAsJsonPrimitive("name").asString
            url = jsonObject.getAsJsonPrimitive("url")?.asString
            icon = jsonObject.getAsJsonPrimitive("icon")?.asString
        }
    }
}

fun EmbedBuilder.applyJson(key: String, value: JsonElement) {
    when (key) {
        "title" -> title = value.asString
        "description" -> description = value.asString
        "author" -> setAuthor(value)
        "url" -> url = value.asString
        "color" -> color = run {
            val internalColor = DungeonHubService.getInstance().gson.fromJson(
                value.asString,
                Color::class.java
            )
            dev.kord.common.Color(internalColor.red, internalColor.green, internalColor.blue)
        }

        "fields" -> setFields(value)
        "footer" -> setFooter(value)
        "timestamp" -> timestamp = (
                DungeonHubService.getInstance().gson.fromJson(
                    value.asString,
                    Instant::class.java
                ).toKotlinInstant()
                )

        "thumbnail" -> setThumbnail(value)
    }
}

fun User.isSelf(): Boolean {
    return id == kord.selfId
}