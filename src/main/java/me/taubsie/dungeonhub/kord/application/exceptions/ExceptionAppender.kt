package me.taubsie.dungeonhub.kord.application.exceptions

import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
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
import java.util.*
import java.util.stream.Collectors

//TODO remove warning due to plugin scanning being deprecated (in log4j2.xml)
@Plugin(name = "ExceptionAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
open class ExceptionAppender protected constructor(name: String?, filter: Filter?) :
    AbstractAppender(name, filter, null, true, Property.EMPTY_ARRAY) {
    override fun append(logEvent: LogEvent) {
        val embed = ApplicationService.embed
        embed.color = EmbedColor.NEGATIVE.color
        embed.title = logEvent.message.formattedMessage

        if (logEvent.thrown != null) {
            embed.description = getExceptionMessage(logEvent.thrown)
        }

        runBlocking {
            launch {
                val kord: Kord? = DiscordConnection.bot?.kordRef

                if (kord != null) {
                    ApplicationService.getBotOwner(kord)
                        ?.getDmChannelOrNull()
                        ?.createMessage {
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

        val result = StringBuilder()
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

        return result.toString()
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