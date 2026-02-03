package net.dungeonhub.application.exceptions

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import dev.kord.core.Kord
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.utils.dm
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.exception.PlayerNotFoundException
import java.nio.charset.StandardCharsets

class ExceptionAppender : AppenderBase<ILoggingEvent>() {
    override fun append(logEvent: ILoggingEvent) {
        val throwable = logEvent.throwableProxy

        if(throwable != null && throwable !is ThrowableProxy) {
            return
        }

        if (throwable?.throwable is CommandExecutionWarning || throwable?.throwable is PlayerNotFoundException) {
            return
        }

        val embed = ApplicationService.embed
        embed.color = EmbedColor.Negative.color
        val title = logEvent.formattedMessage

        if (title.length < (EmbedBuilder.Limits.title - 3)) {
            embed.title = title
        } else {
            embed.title = "Title would be too long, see field"
            embed.field("Title") {
                runBlocking {
                    ContentConnection.authenticated().uploadFile(title.toByteArray(StandardCharsets.UTF_8))
                        ?.let { ContentConnection.authenticated().getCdnUrl(it).toString() }
                } ?: title
            }
        }

        embed.title = logEvent.formattedMessage

        if (throwable?.throwable != null) {
            var description = getExceptionMessage(throwable.throwable)

            if (description != null && description.length > 3000) {
                description = runBlocking {
                    ContentConnection.authenticated().uploadFile(description.toByteArray(StandardCharsets.UTF_8))
                        ?.let { ContentConnection.authenticated().getCdnUrl(it).toString() }
                } ?: description
            }

            embed.description = description
        }

        scheduler.launch {
            if(!DiscordConnection.botIsLoaded) return@launch

            val kord: Kord = DiscordConnection.bot.kordRef

            ApplicationService.getBotOwner(kord)?.dm {
                embeds = mutableListOf(embed)
            }
        }
    }

    private fun getExceptionMessage(throwable: Throwable?): String? {
        if (throwable == null) {
            return null
        }

        if (throwable.stackTrace.isEmpty()) {
            throwable.fillInStackTrace()
        }
        return throwable.stackTraceToString()
    }

    companion object {
        private val scheduler = DhScheduler()
    }
}