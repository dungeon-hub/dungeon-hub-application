package me.taubsie.dungeonhub.application.connection

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.cache.data.toData
import dev.kord.core.entity.*
import dev.kord.core.entity.Embed.*
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.core.supplier.RestEntitySupplier
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.gateway.builder.PresenceBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.components.components
import dev.kordex.core.components.linkButton
import dev.kordex.core.i18n.SupportedLocales
import dev.kordex.core.i18n.toKey
import dev.kordex.data.api.DataCollection
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.listener.ServerJoinListener
import me.taubsie.dungeonhub.application.loader.ClassLoader
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartPriority
import me.taubsie.dungeonhub.application.loader.StartupListener
import me.taubsie.dungeonhub.application.misc.EmbedModel
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.service.MoshiService
import net.dungeonhub.wrapper.kord.toJavaColor
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
            "Handling ${DiscordUserConnection.countLinkedUsers() ?: 0} linked users!"
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
            val time = Duration.between(uptime, Instant.now())
                .withNanos(0)
                .withSeconds(0)
                .toKotlinDuration()
                .toString()

            "discord events since $time"
        },
        AppearanceType.Custom to {
            val amount = try {
                DiscordServerConnection.getTotalAmountOfMoneySpent(693263712626278553L)
                    ?: throw CommandExecutionException("Couldn't load the total amount of money spent.")
            } catch (commandExecutionException: CommandExecutionException) {
                0
            }

            "${ApplicationService.makeNumberReadable(amount)} coins spent on Dungeon Hub!"
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
            dataCollectionMode = DataCollection.Extra

            about {
                general {
                    ephemeral = true

                    message {
                        embed {
                            color(EmbedColor.Default)
                            description = "## Thanks for using our bot!\n" +
                                    "While this bot was initially created only to be used on the Dungeon Hub Discord " +
                                    "server, we have since decided to allow it to be used on other servers as well!\n" +
                                    "If you're confused about how to configure the bot, you can check out the " +
                                    "[documentation](https://docs.dungeon-hub.net/) or ask for help " +
                                    "[in our discord](https://discord.dungeon-hub.net/)."
                        }

                        components {
                            linkButton {
                                label = "Documentation".toKey()
                                url = "https://docs.dungeon-hub.net/"
                            }

                            linkButton {
                                label = "Discord".toKey()
                                url = "https://discord.dungeon-hub.net/"
                            }
                        }
                    }
                }
            }

            errorResponse { message, type ->
                embeds = ApplicationService.getErrorEmbeds(type.error, message.translate())
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

            i18n {
                defaultLocale = SupportedLocales.ENGLISH

                applicationCommandLocale(setOf(Locale.GERMAN))

                interactionUserLocaleResolver()
                interactionGuildLocaleResolver()
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
                "${server.name} with id '${server.id}' by ${
                    server.withStrategy(EntitySupplyStrategy.cache).getOwnerOrNull()?.effectiveName ?: "no-name"
                } (${server.ownerId})"
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

            try {
                appearance.first.apply(appearance.second())()
            } catch (exception: Exception) {
                logger.error("Error during reset of appearance.", exception)
            }
        }
    }

    override suspend fun onStart() {
        ServerJoinListener.GUILD_ON_JOIN.addAll(
            bot?.kordRef?.guilds?.map { guild ->
                guild.id.value
            }?.toList()!!
        )
    }

    override suspend fun postStart() {
        logger.info(LINE)
        getServerListMessage().forEach(logger::info)
        logger.info(LINE)

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runBlocking {
                    resetBotAppearance()
                }
            }
        }, Time(System.currentTimeMillis() + 5000), 1000 * 60 * 30)
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
            val internalColor = MoshiService.moshi.adapter(Color::class.java).fromJson(
                value.asString
            )!!
            dev.kord.common.Color(internalColor.red, internalColor.green, internalColor.blue)
        }

        "fields" -> setFields(value)
        "footer" -> setFooter(value)
        "timestamp" -> timestamp = (
                MoshiService.moshi.adapter(Instant::class.java).fromJson(
                    value.asString
                )!!.toKotlinInstant()
                )

        "thumbnail" -> setThumbnail(value)
    }
}

fun Embed.toBuilder(): EmbedBuilder {
    val embed = EmbedBuilder()

    embed.title = title
    embed.description = description
    embed.url = url
    embed.timestamp = timestamp
    embed.color = color
    embed.image = image?.url
    embed.footer = footer?.toBuilder()
    embed.thumbnail = thumbnail?.toBuilder()
    embed.author = author?.toBuilder()
    embed.fields = fields.map { it.toBuilder() }.toMutableList()

    return embed
}

fun Author.toBuilder(): EmbedBuilder.Author {
    val author = EmbedBuilder.Author()

    author.name = name
    author.url = url
    author.icon = iconUrl

    return author
}

fun Field.toBuilder(): EmbedBuilder.Field {
    val field = EmbedBuilder.Field()

    field.name = name
    field.inline = inline
    field.value = value

    return field
}

fun Embed.toModel(): EmbedModel {
    val embed = EmbedModel(
        title,
        description,
        url,
        timestamp?.toJavaInstant(),
        color?.toJavaColor(),
        image?.url,
        footer?.toBuilder(),
        thumbnail?.toBuilder(),
        author?.toModel()
    )

    embed.fields = fields.map { it.toModel() }.toMutableList()

    return embed
}

fun Footer.toBuilder(): EmbedBuilder.Footer {
    val footer = EmbedBuilder.Footer()

    footer.text = text
    footer.icon = iconUrl

    return footer
}

fun Thumbnail.toBuilder(): EmbedBuilder.Thumbnail? {
    val url = url ?: return null

    val thumbnail = EmbedBuilder.Thumbnail()

    thumbnail.url = url

    return thumbnail
}

fun Author.toModel(): EmbedModel.Author {
    return EmbedModel.Author(name, url, iconUrl)
}

fun Field.toModel(): EmbedModel.Field {
    return EmbedModel.Field(name, inline, value)
}

fun User.isSelf(): Boolean {
    return id == kord.selfId
}

suspend fun RestEntitySupplier.getGuildOrNull(id: Snowflake, withCounts: Boolean = false): Guild? {
    return try {
        Guild(kord.rest.guild.getGuild(id, withCounts).toData(), kord)
    } catch (exception: RestRequestException) {
        if (exception.status.code == 404) null
        else throw exception
    }
}