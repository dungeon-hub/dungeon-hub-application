package me.taubsie.carrylogs.application.start;

import lombok.Getter;
import me.taubsie.carrylogs.application.listener.AutoCompleteListener;
import me.taubsie.carrylogs.application.listener.MessageComponentListener;
import me.taubsie.carrylogs.application.listener.MessageListener;
import me.taubsie.carrylogs.application.listener.SlashCommandListener;
import me.taubsie.carrylogs.*;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ProfileModerationService;
import me.taubsie.carrylogs.config.ConfigProperty;
import me.taubsie.carrylogs.config.ConfigService;
import me.taubsie.carrylogs.config.ConfigType;
import me.taubsie.carrylogs.application.enums.IdList;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class StartBot
{
    @Getter
    private static StartBot instance;
    @Getter
    private DiscordApi bot;
    @Getter
    private final Map<Long, CarryInformation> carryInformation = new HashMap<>();

    public static void main(String[] args)
    {
        ConfigService.setInstance(ConfigType.APPLICATION);

        instance = new StartBot();

        instance.startup();

        ApplicationService.getInstance();
    }

    public void startup()
    {
        bot = new DiscordApiBuilder()
                .setToken(ConfigProperty.DISCORD_BOT_TOKEN.getValue())
                .setAllNonPrivilegedIntents()
                .addIntents(Intent.MESSAGE_CONTENT)
                .setWaitForServersOnStartup(true)
                .login()
                .join();

        bot.updateActivity(ActivityType.WATCHING, "carriers");
        bot.updateStatus(UserStatus.ONLINE);

        bot.bulkOverwriteGlobalApplicationCommands(getGlobalCommands());

        bot.bulkOverwriteServerApplicationCommands(IdList.SERVER.getID(), getServerCommands());
        bot.bulkOverwriteServerApplicationCommands(IdList.SERVER.getTEST_ID(), getServerCommands());

        bot.addListener(new SlashCommandListener());
        bot.addListener(new AutoCompleteListener());
        bot.addListener(new MessageListener());
        bot.addListener(new MessageComponentListener());
    }

    // </commandName:ID>
    public Set<SlashCommandBuilder> getGlobalCommands()
    {
        Set<SlashCommandBuilder> commands = new HashSet<>();

        //Command: score
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to check the carries for.")
                .setRequired(false)
                .build();

        SlashCommandBuilder carryCountCommandBuilder = new SlashCommandBuilder()
                .setName("score")
                .setDescription("Use this to count your or another user's carries.")
                .setOptions(Collections.singletonList(userOption));

        commands.add(carryCountCommandBuilder);

        //Command: calc-price
        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you want.")
                .setLongMaxValue(200)
                .setRequired(true)
                .build();

        SlashCommandOption typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("The type of the carry you want.")
                .setRequired(false)
                .setAutocompletable(true)
                .build();

        SlashCommandBuilder calculatePriceCommandBuilder = new SlashCommandBuilder()
                .setName("calc-price")
                .setDescription("Calculate the price for some amount of carries.")
                .setOptions(Arrays.asList(amountOption, typeOption));

        commands.add(calculatePriceCommandBuilder);

        //Command: score-help
        commands.add(new SlashCommandBuilder().setName("score-help").setDescription("Show an explanation about how score works."));

        //Command: help
        commands.add(new SlashCommandBuilder().setName("help").setDescription("List of available commands."));

        return commands;
    }

    public Set<SlashCommandBuilder> getServerCommands()
    {
        Set<SlashCommandBuilder> commands = new HashSet<>();

        //Command: log
        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you did.")
                .setLongMaxValue(200)
                .setRequired(true)
                .build();

        SlashCommandOption typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("The type of the carry.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        SlashCommandBuilder logCommandBuilder = new SlashCommandBuilder()
                .setName("log")
                .setDescription("Use this to log your carries.")
                .setEnabledInDms(false)
                .setOptions(Arrays.asList(amountOption, typeOption));

        commands.add(logCommandBuilder);

        //Command: manage-score
        SlashCommandBuilder manageScoreCommandBuilder = new SlashCommandBuilder()
                .setName("manage-score")
                .setDescription("Use this to manage the score of carriers.")
                .setEnabledInDms(false)
                .setDefaultEnabledForPermissions(
                        PermissionType.ADMINISTRATOR,
                        PermissionType.MANAGE_SERVER,
                        PermissionType.MANAGE_MESSAGES
                );

        commands.add(manageScoreCommandBuilder);

        return commands;
    }

    public boolean mayDiscardOthers(User user, Server server)
    {
        Set<PermissionType> allowedPermissions = server.getAllowedPermissions(user);

        return allowedPermissions.contains(PermissionType.ADMINISTRATOR)
                || allowedPermissions.contains(PermissionType.MANAGE_SERVER)
                || allowedPermissions.contains(PermissionType.MANAGE_MESSAGES);
    }
}