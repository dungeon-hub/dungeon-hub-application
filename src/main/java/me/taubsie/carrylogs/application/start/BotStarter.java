package me.taubsie.carrylogs.application.start;

import lombok.Getter;
import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.dungeonhub.common.OnStart;
import me.taubsie.dungeonhub.common.ProgramOrigin;
import me.taubsie.dungeonhub.common.StartupListener;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import me.taubsie.dungeonhub.common.config.ConfigType;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@OnStart(priority = 1)
public class BotStarter implements StartupListener, ProgramOrigin {
    private static final Logger logger = LoggerFactory.getLogger(BotStarter.class);

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

        logger.info(getLine());
        getServerListMessage().forEach(logger::info);
        logger.info(getLine());
    }

    public List<String> getServerListMessage() {
        List<String> message = new ArrayList<>();

        message.add("Im on servers:");
        message.addAll(bot.getServers().stream()
                .map(server -> String.format("%s by %s (%d)",
                        server.getName(),
                        server.getOwner().map(User::getDiscriminatedName).orElse("no-name"),
                        server.getOwnerId()))
                .toList());

        return message;
    }

    public String getLine() {
        return "--------------------";
    }

    private void resetBotActivity() {
        bot.updateActivity(ActivityType.WATCHING,
                bot.getServers().stream().mapToInt(Server::getMemberCount).sum() + " carriers on " + bot.getServers().size() + " servers");
    }

    private void resetBotStatus() {
        bot.updateStatus(UserStatus.ONLINE);
    }

    public void resetBotAppearance() {
        resetBotActivity();
        resetBotStatus();
    }

    @Override
    public ConfigType getConfigType() {
        return ConfigType.APPLICATION;
    }
}