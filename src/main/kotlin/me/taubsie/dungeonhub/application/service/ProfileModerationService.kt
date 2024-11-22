package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.AllowedMentionsBuilder
import dev.kordex.core.utils.dm
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.enums.ServerProperty
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadException
import net.codebox.homoglyph.Homoglyph
import net.codebox.homoglyph.HomoglyphBuilder
import net.dungeonhub.connection.DiscordUserConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.stream.Collectors
import kotlin.time.toKotlinDuration

object ProfileModerationService {
    private val logger: Logger = LoggerFactory.getLogger(ProfileModerationService::class.java)

    private val forbiddenUsernames: Array<String> = arrayOf(
        "Captcha.bot",
        "YAGPDB",
        "Giveaway Bot",
        "Giveaway Boat",
        "Ticket Tool",
        "Dyno",
        "Carl-bot",
        "Xenon",
        "SkyKings",
        "SkyHelper",
        "MEE6",
        "Dungeon Hub",
        "Wick",
        "Lunar",
        "Badlion",
        "Hypixel",
        "Syntax",
        "Sеcurity",
        "Bouncr",
        "Base"
    )
    private val excludedIds: Array<Long> = arrayOf(
        727320030462869515L,
        703035551330205716L,
        599475365471059978L,
        678580255384141824L,
        633350165574451200L,
        577147388255272970L,
        1097692461452767272L,
        928744398571831359L,
        542229014681747456L,
        1059959722549190728L,
        1119562575458336828L
    )
    private var homoglyph: Homoglyph? = null

    init {
        try {
            this.homoglyph = HomoglyphBuilder.build()
        } catch (ioException: IOException) {
            throw FailedToLoadException(ioException)
        }
    }

    fun checkUserName(userName: String): String? {
        val searchResults = homoglyph!!.search(userName, *forbiddenUsernames)

        if (searchResults.isEmpty()) {
            return null
        }

        return searchResults.stream().map { searchResult: Homoglyph.SearchResult -> searchResult.match }
            .collect(Collectors.joining("; "))
    }

    private fun isBanDisabled(serverId: Long): Boolean {
        return ServerProperty.PROFILE_MODERATION_BAN.getValue(serverId)
            .map { s: String? ->
                s.equals(
                    "false",
                    ignoreCase = true
                )
            }
            .orElse(java.lang.Boolean.TRUE)
    }

    fun handleUserBan(server: Guild, user: User, executor: User, reason: String) {
        dmBannedPerson(server, user, reason)

        executeBan(server, user, executor, reason)
    }

    fun handleUserBan(server: Guild, user: User, username: String) {
        dmBannedPerson(server, user)

        executeBan(server, user, username)
    }

    private fun sendDm(user: User, message: String, unbanForm: String?) {
        runBlocking {
            launch {
                try {
                    if (unbanForm == null) {
                        user.dm(message)
                    } else {
                        user.dm {
                            val actionRow = ActionRowBuilder()
                            actionRow.linkButton(unbanForm) { label = "Appeal" }

                            content = message
                            components?.add(actionRow)
                        }
                    }
                } catch (_: RequestException) {
                    //ignored since this doesn't matter
                }
            }
        }
    }

    private fun dmBannedPerson(server: Guild, user: User, reason: String) {
        val unbanForm = ServerProperty.UNBAN_FORM.getValue(server.id.value.toLong())

        var message = ServerProperty.BAN_MESSAGE
            .getValue(server.id.value.toLong())
            .orElse("You got banned from `%server%` because of \"$reason\".\nIf you think this is a mistake, contact the administrators for further information.")
            .replace("%server%", server.name)

        if (unbanForm.isPresent) {
            message = message.replace("%form%", unbanForm.get())
        }

        try {
            sendDm(user, message, unbanForm.orElse(null))
        } catch (exception: Exception) {
            logger.error("Error when trying to handle user ban.", exception)
        }
    }

    private fun dmBannedPerson(server: Guild, user: User) {
        if (isBanDisabled(server.id.value.toLong())) {
            return
        }

        val unbanForm = ServerProperty.UNBAN_FORM.getValue(server.id.value.toLong())

        var message = ServerProperty.BAN_MESSAGE
            .getValue(server.id.value.toLong())
            .orElse("You got banned from `%server%` because of a suspicious user profile.\nIf you think this is a mistake, contact the administrators for further information.")
            .replace("%server%", server.name)

        if (unbanForm.isPresent) {
            message = message.replace("%form%", unbanForm.get())
        }

        try {
            sendDm(user, message, unbanForm.orElse(null))
        } catch (exception: Exception) {
            logger.error("Error when trying to handle user ban.", exception)
        }
    }

    private fun executeBan(server: Guild, user: User, username: String) {
        runBlocking {
            launch {
                ServerProperty.MODERATION_LOGS_CHANNEL
                    .getValue(server.id.value.toLong())
                    .map { s: String ->
                        return@map async {
                            server.getChannel(Snowflake(s))
                        }
                    }
                    .ifPresent { serverTextChannel ->
                        runBlocking {
                            launch {
                                serverTextChannel.await().asChannelOfOrNull<MessageChannel>()?.createMessage(
                                    "User ${user.mention} got flagged because of a bad username: \n$username"
                                )
                            }
                        }
                    }
            }

            if (isBanDisabled(server.id.value.toLong())) {
                return@runBlocking
            }

            launch {
                server.ban(user.id) {
                    deleteMessageDuration = Duration.of(6, ChronoUnit.DAYS).toKotlinDuration()
                    reason = "Bad username: $username"
                }
            }
        }
    }

    private fun executeBan(server: Guild, user: User, executor: User, reason: String) {
        runBlocking {
            launch {
                server.ban(user.id) {
                    deleteMessageDuration = Duration.of(6, ChronoUnit.DAYS).toKotlinDuration()
                    this.reason = "Executor: ${executor.username}, Reason: $reason"
                }
            }

            ServerProperty.MODERATION_LOGS_CHANNEL
                .getValue(server.id.value.toLong())
                .map { s: String ->
                    return@map async {
                        server.getChannel(Snowflake(s))
                    }
                }
                .ifPresent { serverTextChannel ->
                    runBlocking {
                        serverTextChannel.await().asChannelOfOrNull<MessageChannel>()?.createMessage {
                            content =
                                "User ${user.mention} got banned by ${executor.mention} for reason: \n$reason"
                            allowedMentions = AllowedMentionsBuilder()
                        }
                    }
                }
        }
    }

    fun isOverwritten(userId: Long): Boolean {
        return Arrays.stream(excludedIds).anyMatch { id: Long -> id == userId }
    }

    fun isVerified(user: Member): Boolean {
        return DiscordUserConnection.getLinkedById(user.id.value.toLong()) != null
    }

    fun isExcluded(user: Member): Boolean {
        return user.isBot || isOverwritten(user.id.value.toLong()) || isVerified(user)
    }
}