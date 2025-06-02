package me.taubsie.dungeonhub.application.exceptions

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import dev.kord.core.Kord
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.utils.dm
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors
import kotlin.concurrent.thread

class ExceptionAppender : AppenderBase<ILoggingEvent>() {
    override fun append(logEvent: ILoggingEvent) {
        val throwable = logEvent.throwableProxy

        if(throwable != null && throwable !is ThrowableProxy) {
            return
        }

        if (throwable?.throwable is CommandExecutionWarning) {
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
                ContentConnection.uploadFile(title.toByteArray(StandardCharsets.UTF_8))
                    ?.let { ContentConnection.getCdnUrl(it).toString() }
                    ?: title
            }
        }

        embed.title = logEvent.formattedMessage

        if (throwable?.throwable != null) {
            var description = getExceptionMessage(throwable.throwable)

            if (description != null && description.length > 3000) {
                description = ContentConnection.uploadFile(description.toByteArray(StandardCharsets.UTF_8))
                    ?.let { ContentConnection.getCdnUrl(it).toString() }
                    ?: description
            }

            embed.description = description
        }

        thread(start=true) {
            runBlocking {
                val kord: Kord? = DiscordConnection.bot?.kordRef

                if (kord != null) {
                    ApplicationService.getBotOwner(kord)
                        ?.dm {
                            embeds = mutableListOf(embed)
                        }
                }
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

        /*val result = StringBuilder()
            .append("Caused by ")
            .append(throwable.javaClass.name)
            .append(": ")
            .append(throwable.message)
            .append(System.lineSeparator())
            .append(getStacktrace(throwable))

        val nextMessage = getExceptionMessage(throwable.cause)

        if (nextMessage != null) {
            result.append(System.lineSeparator())
                .append(nextMessage)
        }

        return result.toString()*/
    }

    private fun getStacktrace(throwable: Throwable): String {
        return Arrays.stream(throwable.stackTrace)
            .map { obj: StackTraceElement -> obj.toString() }
            .map { s: String -> "> $s" }
            .collect(Collectors.joining(System.lineSeparator()))
    }
}