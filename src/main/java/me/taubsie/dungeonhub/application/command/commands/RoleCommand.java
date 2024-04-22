package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "role", description = "Manage a role.", enabledForPermissions = {PermissionType.MANAGE_ROLES})
public class RoleCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{1023684107877761196L, 693263712626278553L};
    }

    private void addRemove(SlashCommandInteractionOption firstOption) {
        User issuer = getUser();

        boolean add = firstOption.getName().equalsIgnoreCase("add");

        Role role = getRoleOption(firstOption, "role");

        User user = getUserOption(firstOption, "user");

        if (!role.getServer().isOwner(issuer)
                && role.getPosition() >= issuer.getRoles(role.getServer()).stream().mapToInt(Role::getPosition).max().orElse(0)) {
            throw new CommandExecutionException("You aren't allowed to give roles that are higher than those that you have.");
        }

        if (add) {
            user.addRole(role).join();

            respond(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.POSITIVE.getColor())
                    .setDescription("Successfully added " + role.getMentionTag() + " to " + user.getMentionTag() + "."));
        } else {
            user.removeRole(role).join();

            respond(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.POSITIVE.getColor())
                    .setDescription("Successfully removed " + role.getMentionTag() + " from " + user.getMentionTag() + "."));
        }

        //unfortunately, we have to call getUserOption() again to ensure the cached roles are reloaded (javacord moment)
        user = getUserOption(firstOption, "user");

        List<Role> updatedRoles = RolesService.getInstance().updateRoles(user, getServer());

        NicknameService.getInstance().updateNickname(user, getServer(), updatedRoles);
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption firstOption = getOptionAtIndex(0);

        switch (firstOption.getName().toLowerCase()) {
            case "add", "remove" -> addRemove(firstOption);
            default -> throw new InvalidSubCommandException();
        }
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption roleOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.ROLE)
                .setName("role")
                .setDescription("Select which role you mean.")
                .setRequired(true)
                .build();

        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("Select which user to modify the role of.")
                .setRequired(true)
                .build();

        SlashCommandOption addOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("add")
                .setDescription("Add a role to a user")
                .setOptions(List.of(userOption, roleOption))
                .build();

        SlashCommandOption removeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("remove")
                .setDescription("Remove a role from a user")
                .setOptions(List.of(userOption, roleOption))
                .build();

        return List.of(addOption, removeOption);
    }
}