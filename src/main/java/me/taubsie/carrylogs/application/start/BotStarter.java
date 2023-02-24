package me.taubsie.carrylogs.application.start;

import lombok.Getter;
import me.taubsie.carrylogs.*;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import me.taubsie.carrylogs.config.ConfigType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.user.UserStatus;

import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@OnStart
public class BotStarter extends ProgramOrigin implements StartupListener {
    private static final Logger logger = LogManager.getLogger(BotStarter.class);

    private static BotStarter instance;
    @Getter
    private final Map<Long, CarryInformation> carryInformation = new HashMap<>();
    @Getter
    private DiscordApi bot;

    public static void main(String[] args) {
        ApplicationClassLoaderService.getInstance().loadStartupListeners();
        ApplicationClassLoaderService.getInstance().executeStartup(BotStarter.getInstance());
    }

    public static BotStarter getInstance() {
        if(instance == null) {
            instance = new BotStarter();
        }

        return instance;
    }

    @Override
    public void onStart(ProgramOrigin programOrigin) {
        bot = ApplicationService.getInstance().getApiBuilder().login().join();

        resetBotAppearance();

        ApplicationClassLoaderService.getInstance().loadListeners(bot);

        ApplicationClassLoaderService.getInstance().loadGlobalSlashCommands(bot);
        ApplicationClassLoaderService.getInstance().loadServerSlashCommands(bot);
    }

    public void resetBotActivity() {
        bot.updateActivity(ActivityType.WATCHING, "carriers on " + bot.getServers().size() + " servers");
    }

    private void resetBotStatus() {
        bot.updateStatus(UserStatus.ONLINE);
    }

    private void resetBotAppearance() {
        resetBotActivity();
        resetBotStatus();
    }

    @Override
    public ConfigType getConfigType() {
        return ConfigType.APPLICATION;
    }

    @Override
    public void log(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}