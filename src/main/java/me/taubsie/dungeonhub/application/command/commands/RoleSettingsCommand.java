package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.kord.application.exceptions.NoOptionFoundException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleCreationModel;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleUpdateModel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

//TODO how to reset a value?
@CommandParameters(name = "role-settings", description = "Change the settings of a role.", enabledForUsers =
        {356134481452597250L})
public class RoleSettingsCommand extends Command {
    //TODO @Override
    public long[] getEnabledServers() {
        return new long[]{1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();
        Role role = getRoleOption(slashCommandCreateEvent.getSlashCommandInteraction(), "role");
        Optional<String> nameSchema = getOptionalStringOption(slashCommandCreateEvent.getSlashCommandInteraction(),
                "name-schema");
        Optional<Boolean> verifiedRole = getOptionalBooleanOption(slashCommandCreateEvent.getSlashCommandInteraction(),
                "verified-role");

        Optional<DiscordRoleModel> currentRole =
                DiscordRoleConnection.getInstance(server.getId()).getById(role.getId());

        if (nameSchema.isEmpty() && verifiedRole.isEmpty()) {
            if (currentRole.isEmpty()) {
                throw new NoOptionFoundException();
            } else {
                respondEphemeral(ApplicationService.getInstance()
                        .loadEmbedFromDiscordRole(currentRole.get()));
            }
            return;
        }

        Optional<DiscordRoleModel> modifiedRole = currentRole.isPresent()
                ? DiscordRoleConnection.getInstance(server.getId()).updateRole(role.getId(),
                new DiscordRoleUpdateModel(
                        nameSchema.orElse(null),
                        verifiedRole.orElse(null)
                ))
                : DiscordRoleConnection.getInstance(server.getId()).addNewRole(
                new DiscordRoleCreationModel(
                        role.getId(),
                        nameSchema.orElse(null),
                        verifiedRole.orElse(false)
                ));

        if (modifiedRole.isEmpty()) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setDescription("Couldn't modify the given role."));
            return;
        }

        respond(ApplicationService.getInstance()
                .loadEmbedFromDiscordRole(modifiedRole.get())
                .setColor(EmbedColor.POSITIVE.getColor())
                .setTitle("Modified role"));
    }

    //TODO @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption roleOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.ROLE)
                .setName("role")
                .setDescription("The role which settings you'd like to change.")
                .setRequired(true)
                .build();

        SlashCommandOption nameSchemaOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("name-schema")
                .setDescription("Set the name schema for this username")
                .setRequired(false)
                .build();

        SlashCommandOption verifiedRoleOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("verified-role")
                .setDescription("Set if the role should automatically be granted to everyone who is linked.")
                .setRequired(false)
                .build();

        return List.of(roleOption, nameSchemaOption, verifiedRoleOption);
    }
}