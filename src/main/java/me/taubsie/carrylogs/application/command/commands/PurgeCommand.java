package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.dungeonhub.common.CarryRole;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.ServerUpdater;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@CommandParameters(name = "purge",
        description = "Allows you to purge inactive carriers.",
        enabledForPermissions = {PermissionType.ADMINISTRATOR})
public class PurgeCommand extends Command {
    private static final List<String> choices = List.of("dungeons", "slayer");
    private static final Logger logger = LoggerFactory.getLogger(PurgeCommand.class);

    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        long threshold = getLongOption("threshold");
        String purgeType = getStringOption("purge-type");

        Map<Long, Long> purgeData = new HashMap<>();
        List<IdList> rolesToRemove = new ArrayList<>();

        switch(purgeType) {
            case "dungeons" -> {
                purgeData.putAll(ConnectionService.getInstance().getPurgeableUsers(threshold, "dungeons"));
                rolesToRemove.addAll(List.of(IdList.getDungeonCarryRoles()));
            }
            case "slayer" -> {
                purgeData.putAll(ConnectionService.getInstance().getPurgeableUsers(threshold, "slayer"));
                rolesToRemove.addAll(List.of(IdList.getSlayerCarryRoles()));
            }
            default ->
                    throw new InvalidOptionException("purge-type", "Please enter a valid purge-type (" + String.join(", ", choices + ")"));
        }

        if(purgeData.isEmpty()) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.INFORMATION.getColor())
                    .setTitle("No users to purge found."));
            return;
        }

        InteractionOriginalResponseUpdater updater = slashCommandCreateEvent.getSlashCommandInteraction().respondLater().join();
        ServerUpdater serverUpdater = getServer().createUpdater();

        int amount = 0;
        List<String> purgeDisplay = new ArrayList<>();

        for(Map.Entry<Long, Long> entry : purgeData.entrySet()) {
            User carrier = slashCommandCreateEvent.getSlashCommandInteraction().getApi().getUserById(entry.getKey()).join();

            purgeDisplay.add(carrier.getMentionTag() + " - " + entry.getValue() + " score");

            for(IdList carryRole : rolesToRemove) {
                Optional<Role> role = getServer().getRoleById(carryRole.getLocalId(getServer().getId()));

                if(role.isEmpty()) {
                    logger.error("Role " + carryRole.name() + " not found on server " + getServer().getId());
                    return;
                }

                serverUpdater.removeRoleFromUser(carrier, role.get());
            }

            List<CarryRole> roleList =
                    IdList.getCarryRoles(carrier.getRoles(getServer()), getServer().getId()).stream().map(IdList::getCarryRole).toList();

            if(!roleList.isEmpty()) {
                ConnectionService.getInstance().addRoles(carrier.getId(), roleList);
            }

            amount++;
        }

        serverUpdater.setAuditLogReason("Purge of type \"" + purgeType + "\" with threshold " + threshold + ".")
                .update().join();

        updater.addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.DEFAULT.getColor())
                        .setTitle("Removed the roles of " + amount + " carriers.")
                        .setDescription(String.join("\n", purgeDisplay)))
                .update();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption carryAmountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("threshold")
                .setDescription("The score-threshold.")
                .setLongMaxValue(50L)
                .setLongMinValue(0L)
                .setRequired(true)
                .build();

        SlashCommandOptionBuilder carryTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("purge-type")
                .setDescription("The type of purge.")
                .setRequired(true);

        choices.forEach(s -> carryTypeOption.addChoice(s, s));

        return Arrays.asList(carryAmountOption, carryTypeOption.build());
    }
}