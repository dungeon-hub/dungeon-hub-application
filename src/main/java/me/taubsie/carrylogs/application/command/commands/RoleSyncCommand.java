package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.dungeonhub.common.CarryRole;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CommandParameters(name = "rolesync",
        description = "Test command for adding carriers to database.",
        enabledForPermissions = {PermissionType.ADMINISTRATOR})
public class RoleSyncCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        if(!slashCommandCreateEvent.getSlashCommandInteraction().getUser().isBotOwnerOrTeamMember()) {
            throw new MissingPermissionException();
        }

        InteractionOriginalResponseUpdater responseUpdater = slashCommandCreateEvent.getSlashCommandInteraction().respondLater().join();
        Server server = getServer();

        Map<Long, List<CarryRole>> roleList = Arrays.stream(IdList.getCarryRoles())
                .map(idList -> server.getRoleById(idList.getLocalId(server.getId())))
                .flatMap(Optional::stream)
                .flatMap(role -> role.getUsers().stream().filter(user -> !user.isBot()))
                .distinct()
                .collect(Collectors.toMap(
                        DiscordEntity::getId,
                        user -> IdList.getCarryRoles(user.getRoles(server), server.getId()).stream()
                                .map(IdList::getCarryRole)
                                .toList()));

        ConnectionService.getInstance().addMultipleRoles(roleList);

        responseUpdater.addEmbed(ApplicationService.getInstance().getEmbed()
                        .setColor(new Color(255, 255, 255 /*TODO change color*/))
                        .setTitle("Role-Sync")
                        .setDescription("Changed the internal roles of " + roleList.size() + " users."))
                .update();
    }
}