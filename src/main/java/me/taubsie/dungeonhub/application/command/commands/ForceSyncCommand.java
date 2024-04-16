package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "force-sync", description = "Forces the update of the users roles and nickname.", enabledForPermissions = {PermissionType.MANAGE_ROLES})
public class ForceSyncCommand extends Command {
    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to sync.")
                .setRequired(true)
                .build();

        return List.of(userOption);
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();
        User user = getUserOption("user");

        RolesService.getInstance().updateRoles(user, server);
        try {
            NicknameService.getInstance().updateNickname(user, server);
            respond(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.POSITIVE.getColor())
                    .setDescription("Username and roles were synced!"));
        } catch (NotLinkedException e) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setDescription("The user is not linked, their roles were synced!"));
        }
    }
}