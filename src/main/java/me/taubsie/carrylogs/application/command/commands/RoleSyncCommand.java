package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.CarryRole;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;
import java.util.List;

@CommandParameters(name = "rolesync",
        description = "Test command for adding carriers to database.",
        enabledForPermissions = {PermissionType.ADMINISTRATOR},
        enabledServers = {693263712626278553L, 1023684107877761196L})
public class RoleSyncCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        if (!slashCommandCreateEvent.getSlashCommandInteraction().getUser().isBotOwnerOrTeamMember()) {
            throw new MissingPermissionException();
        }

        int count = 0;
        for (User user : getServer().getMembers()) {
            if (user.isBot()) {
                continue;
            }

            List<CarryRole> roleList =
                    IdList.getCarryRoles(user.getRoles(getServer()), getServer().getId()).stream().map(IdList::getCarryRole).toList();

            if (!roleList.isEmpty()) {
                count++;
                ConnectionService.getInstance().addRoles(user.getId(), roleList);
            }
        }
        respondEphemeral(ApplicationService.getInstance().getEmbed()
                .setColor(new Color(255, 255, 255 /*TODO change color*/))
                .setTitle("Role-Sync")
                .setDescription("Changed the internal roles of " + count + " users."));
    }
}