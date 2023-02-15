package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

@CommandParameters(name = "rolesync",
                   description = "Test command for adding carriers to database.",
                   enabledForPermissions = {PermissionType.ADMINISTRATOR},
                   enabledServers = {693263712626278553L, 1023684107877761196L})
public class RoleSyncCommand extends Command
{
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        if (!slashCommandCreateEvent.getSlashCommandInteraction().getUser().isBotOwnerOrTeamMember())
        {
            throw new MissingPermissionException();
        }

        //TODO finish implementation
    }
}