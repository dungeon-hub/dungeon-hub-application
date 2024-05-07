package me.taubsie.dungeonhub.kord.application.connection

import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.listener.ServerJoinListener
import me.taubsie.dungeonhub.kord.application.loader.ClassLoader
import me.taubsie.dungeonhub.kord.application.loader.OnStart
import me.taubsie.dungeonhub.kord.application.loader.StartPriority
import me.taubsie.dungeonhub.kord.application.loader.StartupListener
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
        bot = ExtensibleBot(ConfigProperty.DISCORD_BOT_TOKEN.value) {
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
    suspend fun resetBotAppearance() {
        bot?.kordRef?.editPresence {
            val name = DiscordUserConnection.getInstance().countLinkedUsers().orElse("0") +
                    " carriers on " +
                    bot?.kordRef?.guilds?.count() +
                    " servers"

            watching(name)
            status = PresenceStatus.Online
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
        resetBotAppearance()
    }
}

suspend fun User.getMutualServers(): Flow<Member> {
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