package me.taubsie.carrylogs.application.start;

import lombok.Getter;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.listener.*;
import me.taubsie.carrylogs.*;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ClassLoaderService;
import me.taubsie.carrylogs.config.ConfigProperty;
import me.taubsie.carrylogs.config.ConfigService;
import me.taubsie.carrylogs.config.ConfigType;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.interaction.*;

import java.util.*;
import java.util.stream.Collectors;

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
    @Getter
    private final Map<SlashCommand, Command> slashCommandMap = new HashMap<>();

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
                .addIntents(Intent.MESSAGE_CONTENT, Intent.GUILD_MEMBERS)
                .setWaitForServersOnStartup(true)
                .login()
                .join();

        bot.updateActivity(ActivityType.WATCHING, "carriers on " + bot.getServers().size() + " servers");
        bot.updateStatus(UserStatus.ONLINE);

        loadSlashCommands();

        bot.addListener(new SlashCommandListener());
        bot.addListener(new AutoCompleteListener());
        bot.addListener(new MessageListener());
        bot.addListener(new MessageComponentListener());
        bot.addListener(new MemberJoinListener());
        bot.addListener(new MemberProfileChangeListener());
    }

    private void loadSlashCommands()
    {
        Set<ApplicationCommand> globalCommands = bot.bulkOverwriteGlobalApplicationCommands(ClassLoaderService.getInstance().getCommandMap().entrySet().stream().filter(entry -> entry.getValue().isGlobal()).map(Map.Entry::getKey).collect(Collectors.toSet())).join();

        for (ApplicationCommand applicationCommand : globalCommands)
        {
            if (applicationCommand instanceof SlashCommand slashCommand)
            {
                Command command = ClassLoaderService.getInstance().getCommandMap().values().stream().filter(command1 -> command1.getCommandName().equalsIgnoreCase(applicationCommand.getName())).findFirst().orElse(null);
                slashCommandMap.put(slashCommand, command);
            }
        }

        Map<Long, Set<SlashCommandBuilder>> serverCommandBuilders = new HashMap<>();

        ClassLoaderService.getInstance()
                .getCommandMap()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isGlobal())
                .forEach(entry ->
                        {
                            long[] serverIds = entry.getValue().getEnabledServers();
                            for (long id : serverIds)
                            {
                                Set<SlashCommandBuilder> resultSet = serverCommandBuilders.containsKey(id)
                                        ? serverCommandBuilders.get(id)
                                        : new HashSet<>();

                                resultSet.add(entry.getKey());
                                serverCommandBuilders.put(id, resultSet);
                            }
                        }
                );

        for (Map.Entry<Long, Set<SlashCommandBuilder>> entry : serverCommandBuilders.entrySet())
        {
            Set<ApplicationCommand> serverCommands = bot.bulkOverwriteServerApplicationCommands(entry.getKey(), entry.getValue()).join();
            for (ApplicationCommand applicationCommand : serverCommands)
            {
                if (applicationCommand instanceof SlashCommand slashCommand)
                {
                    Command command = ClassLoaderService.getInstance().getCommandMap().values().stream().filter(command1 -> command1.getCommandName().equalsIgnoreCase(applicationCommand.getName())).findFirst().orElse(null);
                    slashCommandMap.put(slashCommand, command);
                }
            }
        }
    }

    public boolean mayDiscardOthers(User user, Server server)
    {
        Set<PermissionType> allowedPermissions = server.getAllowedPermissions(user);

        return allowedPermissions.contains(PermissionType.ADMINISTRATOR)
                || allowedPermissions.contains(PermissionType.MANAGE_SERVER)
                || allowedPermissions.contains(PermissionType.MANAGE_MESSAGES);
    }

    public boolean mayManageScore(User user, Server server)
    {
        Set<PermissionType> allowedPermissions = server.getAllowedPermissions(user);

        return allowedPermissions.contains(PermissionType.ADMINISTRATOR)
                || allowedPermissions.contains(PermissionType.MANAGE_SERVER)
                || allowedPermissions.contains(PermissionType.MANAGE_MESSAGES);
    }
}