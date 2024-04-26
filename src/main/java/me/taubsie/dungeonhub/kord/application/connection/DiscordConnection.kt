package me.taubsie.dungeonhub.kord.application.connection

import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.config.ConfigService
import me.taubsie.dungeonhub.kord.application.commands.HelpCommand
import me.taubsie.dungeonhub.kord.application.commands.TestCommand
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.loader.StartupListener
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DiscordConnection : StartupListener {
    private val logger: Logger = LoggerFactory.getLogger(DiscordConnection::class.java)

    var bot: ExtensibleBot? = null

    private const val LINE = "----------------------------------------"

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            launch {
                //TODO make it work automatically through class scanning (or similar)
                ConfigService.getInstance().preStart()
                ConfigService.getInstance().onStart()
                ConfigService.getInstance().postStart()

                onStart()
            }
        }
    }

    override suspend fun onStart() {
        bot =
            ExtensibleBot(ConfigProperty.DISCORD_BOT_TOKEN.value) {
                extensions {
                    add { TestCommand() }
                    add { HelpCommand() }
                }

                @OptIn(PrivilegedIntent::class)
                intents {
                    //+Intent.GuildMembers
                    +Intent.MessageContent
                }
            }

        /*kord.on<MessageCreateEvent> { // runs every time a message is created that our bot can read

            // ignore other bots, even ourselves. We only serve humans here!
            if (message.author?.isBot != false) return@on

            // check if our command is being invoked
            if (message.content != "!ping") return@on

            // all clear, give them the pong!
            message.channel.createMessage("pong!")
        }*/

        bot?.start()

        resetBotAppearance()

        //ClassLoaderService.getInstance().loadListeners(bot)

        //ClassLoaderService.getInstance().loadGlobalSlashCommands(bot)
        //ClassLoaderService.getInstance().loadServerSlashCommands(bot)

        logger.info(LINE)
        getServerListMessage().forEach(logger::info)
        logger.info(LINE)
    }

    private suspend fun getServerListMessage(): List<String> {
        val message: MutableList<String> = mutableListOf()

        message.add("Im on servers:")
        message.addAll(
            bot?.kordRef?.guilds?.map { server ->
                String.format(
                    "%s with id '%d' by %s (%d)",
                    server.name,
                    server.id.value,
                    server.getOwnerOrNull()?.effectiveName ?: "no-name",
                    server.ownerId
                )
            }!!.toList()
        )

        return message
    }

    private suspend fun resetBotAppearance() {
        bot?.kordRef?.editPresence {
            val name = bot?.kordRef?.guilds?.map { value -> value.memberCount!! }?.reduce { a, b -> a + b }.toString() +
                    " carriers on " +
                    bot?.kordRef?.guilds?.count() +
                    " servers"

            watching(name)
        }
    }
}