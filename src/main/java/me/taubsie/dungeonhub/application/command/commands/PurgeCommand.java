package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.PurgeData;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.PurgeTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.PurgingService;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.PurgeTypeRoleModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.purge_type.PurgeTypeModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "purge",
        description = "Allows you to purge inactive carriers.",
        enabledForPermissions = {PermissionType.ADMINISTRATOR})
public class PurgeCommand extends Command {
    public static final String FIELD_NAME = "purge-type";
    private static final Logger logger = LoggerFactory.getLogger(PurgeCommand.class);

    public static SlashCommandOption getPurgeTypeOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName(FIELD_NAME)
                .setDescription("The identifier of the purge type")
                .setRequired(true)
                .setMaxLength(30)
                .setAutocompletable(true)
                .build();
    }

    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();

        long threshold = getLongOption("threshold");

        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(server.getId())
                .getByIdentifier(getStringOption(CarryTypeCommand.FIELD_NAME));

        if (carryType.isEmpty()) {
            throw new InvalidOptionException(CarryTypeCommand.FIELD_NAME, "Carry Type couldn't be found.");
        }

        PurgeTypeModel purgeType = PurgeTypeConnection.getInstance(carryType.get())
                .getByIdentifier(getStringOption(CarryTypeCommand.FIELD_NAME))
                .orElseThrow(() -> new InvalidOptionException(FIELD_NAME, "Purge Type couldn't be found."));

        List<DiscordRoleModel> rolesToRemove = purgeType.getPurgeTypeRoleModels().stream()
                .map(PurgeTypeRoleModel::getDiscordRoleModel)
                .toList();

        List<ScoreModel> scores = ScoreConnection.getInstance(carryType.get())
                .getScores()
                .orElse(List.of()).stream()
                .filter(scoreModel -> scoreModel.getScoreType() == ScoreType.DEFAULT)
                .toList();

        InteractionOriginalResponseUpdater updater =
                slashCommandCreateEvent.getSlashCommandInteraction().respondLater().join();

        List<Long> safeCarriers = scores.stream()
                .filter(scoreModel -> scoreModel.getScoreAmount() != null)
                .filter(scoreModel -> scoreModel.getScoreAmount() >= threshold)
                .map(ScoreModel::getCarrier)
                .map(DiscordUserModel::getId)
                .toList();

        List<User> purgeCarriers = rolesToRemove.stream()
                .map(DiscordRoleModel::getId)
                .distinct()
                .flatMap(roleId -> server.getRoleById(roleId).stream())
                .flatMap(role -> role.getUsers().stream())
                .distinct()
                .filter(user -> !safeCarriers.contains(user.getId()))
                .toList();

        int amount = 0;
        List<String> purgeDisplay = new ArrayList<>();

        for (User carrier : purgeCarriers) {
            long score = scores.stream()
                    .filter(scoreModel -> scoreModel.getCarrier().getId() == carrier.getId())
                    .map(ScoreModel::getScoreAmount)
                    .findFirst()
                    .orElse(0L);

            PurgeData purgeData = new PurgeData(carrier.getId(), rolesToRemove, score, purgeType, threshold);

            PurgingService.getInstance().addPurgeData(purgeData);

            purgeDisplay.add(carrier.getMentionTag() + " - " + score + " score");

            amount++;
        }

        String purgedList = String.join("\n", purgeDisplay);

        String description;

        if (purgedList.length() >= 4000) {
            description = "The list of carriers purged would be too long.\n"
                    + ContentConnection.getInstance()
                    .uploadFile(purgedList.getBytes(StandardCharsets.UTF_8))
                    .map(s -> "https://cdn.dungeon-hub.net/" + s)
                    .orElse("The full list has been logged, contact administrators for more information.");
        } else {
            description = purgedList;
        }

        updater.addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.DEFAULT.getColor())
                        .setTitle("Added the roles of " + amount + " carriers to removal-list.")
                        .setDescription(description))
                .update();

        logger.info("Purge data for type \"{}\":", purgeType.getIdentifier());
        logger.info(purgedList);
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption thresholdOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("threshold")
                .setDescription("The score-threshold.")
                .setLongMaxValue(50L)
                .setLongMinValue(0L)
                .setRequired(true)
                .build();

        return List.of(CarryTypeCommand.getCarryTypeOption(), getPurgeTypeOption(), thresholdOption);
    }
}