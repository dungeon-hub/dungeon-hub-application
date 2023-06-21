package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.classes.PurgeData;
import me.taubsie.carrylogs.application.classes.PurgeType;
import me.taubsie.carrylogs.application.enums.RoleConversion;
import me.taubsie.carrylogs.application.service.PurgingService;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.permission.PermissionType;
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
    private static final Logger logger = LoggerFactory.getLogger(PurgeCommand.class);

    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        long threshold = getLongOption("threshold");

        PurgeType purgeType = getEnumOption("purge-type", PurgeType.class);

        Map<Long, Long> purgeData = purgeType.getPurgeData(threshold, getServer().getId());
        List<RoleConversion> rolesToRemove = purgeType.getRolesToRemove();

        if (purgeData.isEmpty()) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.INFORMATION.getColor())
                    .setTitle("No users to purge found."));
            return;
        }

        InteractionOriginalResponseUpdater updater =
                slashCommandCreateEvent.getSlashCommandInteraction().respondLater().join();

        int amount = 0;
        List<String> purgeDisplay = new ArrayList<>();

        for(Map.Entry<Long, Long> entry : purgeData.entrySet()) {
            User carrier = slashCommandCreateEvent.getApi().getUserById(entry.getKey()).join();

            PurgeData userPurgeData = new PurgeData(getServer().getId(), carrier.getId(), rolesToRemove,
                    entry.getValue(), purgeType, threshold);

            PurgingService.getInstance().addPurgeData(userPurgeData);

            purgeDisplay.add(carrier.getMentionTag() + " - " + entry.getValue() + " score");

            amount++;
        }

        String purgedList = String.join("\n", purgeDisplay);

        updater.addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.DEFAULT.getColor())
                        .setTitle("Added the roles of " + amount + " carriers to removal-list.")
                        .setDescription((purgedList.length() >= 4000)
                                ? "The list of carriers purged would be too long.\nThe full list has been logged, " +
                                "contact administrators for more information."
                                : purgedList))
                .update();

        logger.info("Purge data for type \"{}\":", purgeType);
        logger.info(purgedList);
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

        Arrays.stream(PurgeType.values()).forEach(purgeType -> carryTypeOption.addChoice(purgeType.getDisplayName(),
                purgeType.name()));

        return Arrays.asList(carryAmountOption, carryTypeOption.build());
    }
}