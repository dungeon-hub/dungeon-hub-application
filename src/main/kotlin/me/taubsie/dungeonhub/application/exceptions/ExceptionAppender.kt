package me.taubsie.dungeonhub.application.exceptions

import dev.kord.core.Kord
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.utils.dm
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Core
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors

//TODO remove warning due to plugin scanning being deprecated (in log4j2.xml)
@Plugin(name = "ExceptionAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
open class ExceptionAppender protected constructor(name: String?, filter: Filter?) :
    AbstractAppender(name, filter, null, true, Property.EMPTY_ARRAY) {
    override fun append(logEvent: LogEvent) {
        if (logEvent.thrown is CommandExecutionWarning) {
            return
        }

        val embed = ApplicationService.embed
        embed.color = EmbedColor.NEGATIVE.color
        val title = logEvent.message.formattedMessage

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

        embed.title = logEvent.message.formattedMessage

        if (logEvent.thrown != null) {
            var description = getExceptionMessage(logEvent.thrown)

            if (description != null && description.length > 4000) {
                description = ContentConnection.uploadFile(description.toByteArray(StandardCharsets.UTF_8))
                    ?.let { ContentConnection.getCdnUrl(it).toString() }
                    ?: description
            }

            embed.description = description
        }

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

    private fun getExceptionMessage(throwable: Throwable?): String? {
        if (throwable == null) {
            return null
        }

        //TODO test
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

    companion object {
        @JvmStatic
        @PluginFactory
        fun createAppender(
            @PluginAttribute("name") name: String?,
            @PluginElement("Filter") filter: Filter?
        ): ExceptionAppender {
            return ExceptionAppender(name, filter)
        }
    }
}