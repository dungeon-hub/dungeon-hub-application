package me.taubsie.dungeonhub.application.connection;

import lombok.Getter;
import me.taubsie.dungeonhub.application.service.ApplicationClassLoaderService;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.dungeonhub.common.OnStart;
import me.taubsie.dungeonhub.common.ProgramOrigin;
import me.taubsie.dungeonhub.common.StartupListener;
import me.taubsie.dungeonhub.common.config.ConfigType;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the main-class for the application.
 * It automatically loads all listeners and commands and initiates the bot-instance.
 * Even if it doesn't make much of a difference, it is advised to not use {@link #getBot()} too much.
 *
 * @author Taubsie
 * @since 1.0.0
 */
@OnStart(priority = 1)
public class DiscordConnection implements StartupListener, ProgramOrigin {
    private static final Logger logger = LoggerFactory.getLogger(DiscordConnection.class);
    private static final String LINE = "--------------------";

    private static DiscordConnection instance;
    @Getter
    private final Map<Long, CarryInformation> carryInformation = new HashMap<>();
    @Getter
    private DiscordApi bot;

    /**
     * The main-method that is executed by the JVM.
     *
     * @param args The command-line parameters passed by the JVM.
     */
    public static void main(String[] args) {
        ApplicationClassLoaderService.getInstance().loadStartupListeners();
        ApplicationClassLoaderService.getInstance().executeStartup(DiscordConnection.getInstance());
    }

    /**
     * Returns the current instance of this class.
     *
     * @return the current instance of this class.
     */
    public static DiscordConnection getInstance() {
        if(instance == null) {
            instance = new DiscordConnection();
        }

        return instance;
    }

    /**
     * Method by the {@link StartupListener} interface, this is automatically executed on program launch.
     * This implementation starts the discord-bot.
     *
     * @param programOrigin The origin from which the program was executed, this is needed for {@link #getConfigType()}.
     */
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

    /**
     * Returns the formatted message to list all servers the bot is on.
     *
     * @return the formatted message to list all servers the bot is on.
     */
    public List<String> getServerListMessage() {
        List<String> message = new ArrayList<>();

        message.add("Im on servers:");
        message.addAll(bot.getServers().stream()
                .map(server -> String.format("%s with id '%d' by %s (%d)",
                        server.getName(),
                        server.getId(),
                        server.getOwner().map(User::getDiscriminatedName).orElse("no-name"),
                        server.getOwnerId()))
                .toList());

        return message;
    }

    /**
     * Returns a line for command-line output.
     *
     * @return a line for command-line output.
     */
    public static String getLine() {
        return LINE;
    }

    /**
     * This resets the activity shown on the bot back to the default.
     */
    private void resetBotActivity() {
        bot.updateActivity(ActivityType.WATCHING,
                bot.getServers().stream().mapToInt(Server::getMemberCount).sum() + " carriers on " + bot.getServers().size() + " servers");
    }

    /**
     * This resets the status of the bot back to the default.
     */
    private void resetBotStatus() {
        bot.updateStatus(UserStatus.ONLINE);
    }

    /**
     * This resets the bot's appearance.
     *
     * @see #resetBotActivity()
     * @see #resetBotStatus()
     */
    public void resetBotAppearance() {
        resetBotActivity();
        resetBotStatus();
    }

    /**
     * Returns the config-type of this program origin.
     *
     * @return the config-type of this program origin.
     */
    @Override
    public ConfigType getConfigType() {
        return ConfigType.APPLICATION;
    }
}