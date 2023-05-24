package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.start.BotStarter;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

public class ExceptionAppender extends AbstractAppender {
    protected ExceptionAppender(String name, Filter filter) {
        super(name, filter, null);
    }

    @PluginFactory
    public static ExceptionAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        return new ExceptionAppender(name, filter);
    }

    @Override
    public void append(LogEvent logEvent) {
        ApplicationService.getInstance()
                .getBotOwner(BotStarter.getInstance().getBot())
                .openPrivateChannel()
                .join()
                .sendMessage(logEvent.getMessage().getFormattedMessage());
    }
}