package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ProfileModerationService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@CommandParameters(name = "userscan",
                   description = "Scans for users with a bad username.",
                   enabledForPermissions = {PermissionType.BAN_MEMBERS})
public class UserScanCommand extends Command
{
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        Server server = getServer(slashCommandCreateEvent.getInteraction());

        if (!server.hasPermission(slashCommandCreateEvent.getSlashCommandInteraction().getUser(), PermissionType.ADMINISTRATOR))
        {
            throw new MissingPermissionException();
        }

        Map<User, String> result = new HashMap<>();
        Map<User, String> excluded = new HashMap<>();

        for (User user : server.getMembers())
        {
            if (!user.isBot())
            {
                String checkResult = ProfileModerationService.getInstance().checkUserName(user.getName());
                if (checkResult != null)
                {
                    if (ProfileModerationService.getInstance().isExcluded(user, server))
                    {
                        excluded.put(user, checkResult);
                    }
                    else
                    {
                        result.put(user, checkResult);
                    }
                }
            }
        }

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService.getInstance().getEmbed().setColor(new Color(255, 0, 0 /*TODO color*/)).setDescription("Banned:\n" + result.entrySet()
                        .stream()
                        .map(userStringEntry ->
                                userStringEntry.getKey().getMentionTag() + " - " + userStringEntry.getValue())
                        .collect(Collectors.joining("\n")) + "\n\nExcluded from ban:\n" +
                        excluded.entrySet()
                                .stream()
                                .map(userStringEntry ->
                                        userStringEntry.getKey().getMentionTag() + " - " + userStringEntry.getValue()).collect(Collectors.joining("\n"))))
                .respond();

        for (Map.Entry<User, String> entries : result.entrySet())
        {
            ProfileModerationService.getInstance().handleUserBan(server, entries.getKey(), entries.getValue());
        }
    }
}