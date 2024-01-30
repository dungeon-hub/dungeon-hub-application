package me.taubsie.dungeonhub.application.classes;

import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.Arrays;
import java.util.stream.Collectors;

@Plugin(name = "ExceptionAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class ExceptionAppender extends AbstractAppender {
    protected ExceptionAppender(String name, Filter filter) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static ExceptionAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        return new ExceptionAppender(name, filter);
    }

    @Override
    public void append(LogEvent logEvent) {
        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.NEGATIVE.getColor())
                .setTitle(logEvent.getMessage().getFormattedMessage());

        if(logEvent.getThrown() != null) {
            embed.setDescription(getExceptionMessage(logEvent.getThrown()));
        }

        ApplicationService.getInstance()
                .getBotOwner(DiscordConnection.getInstance().getBot())
                .openPrivateChannel()
                .join()
                .sendMessage(embed);
    }

    private String getExceptionMessage(Throwable throwable) {
        if(throwable == null) {
            return null;
        }

        StringBuilder result = new StringBuilder()
                .append("Caused by ")
                .append(throwable.getClass().getName())
                .append(": ")
                .append(throwable.getMessage())
                .append(System.lineSeparator())
                .append(getStacktrace(throwable));

        String nextMessage = getExceptionMessage(throwable.getCause());

        if(nextMessage != null) {
            result.append(System.lineSeparator())
                    .append(nextMessage);
        }

        return result.toString();
    }

    private String getStacktrace(Throwable throwable) {
        return Arrays.stream(throwable.getStackTrace())
                .map(StackTraceElement::toString)
                .map(s -> "> " + s)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}