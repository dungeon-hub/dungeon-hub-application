package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.MissingPermissionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.application.service.PermissionService;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.common.model.score.ScoreUpdateModel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "manage-score",
        description = "Use this to manage the score of carriers.",
        enabledForPermissions = {PermissionType.MANAGE_MESSAGES})
public class ManageScoreCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();

        if (!PermissionService.getInstance().mayManageServices(slashCommandCreateEvent.getSlashCommandInteraction().getUser(), server)) {
            throw new MissingPermissionException();
        }

        Optional<SlashCommandInteractionOption> addRemoveOption =
                slashCommandCreateEvent.getSlashCommandInteraction().getOptionByIndex(0);

        if (addRemoveOption.isEmpty()) {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("Please either" +
                    " add or remove score.").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        SlashCommandInteractionOption subCommand = addRemoveOption.get();
        boolean removed = subCommand.getName().equalsIgnoreCase("remove");

        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(server.getId())
                .getByIdentifier(getStringOption(subCommand, "carry-type"));

        if (carryType.isEmpty()) {
            throw new InvalidOptionException("carry-type");
        }

        User user = getUserOption(subCommand, "user");

        Long amount = getLongOption(subCommand, "amount");

        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() -> {
            long score = removed ? -amount : amount;

            List<ScoreModel> updatedScores = ScoreConnection.getInstance(carryType.get())
                    .updateScores(new ScoreUpdateModel(user.getId(), score))
                    .orElse(new ArrayList<>());

            long updatedScore = updatedScores.stream()
                    .filter(scoreModel -> scoreModel.getScoreType() == ScoreType.DEFAULT)
                    .map(ScoreModel::getScoreAmount)
                    .findFirst()
                    .orElse(0L);

            Optional<ServerTextChannel> logs = ServerProperty.SCORE_LOGS_CHANNEL
                    .getValue(server.getId())
                    .flatMap(server::getTextChannelById);

            logs.ifPresent(serverTextChannel ->
                    serverTextChannel.sendMessage(ApplicationService
                            .getInstance()
                            .getEmbed()
                            .setColor(EmbedColor.INFORMATION.getColor())
                            .setTitle("Score-Management")
                            .setDescription(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getMentionTag() + " edited the " + carryType.get().getDisplayName() + "-score of " + user.getMentionTag() + ".\nThey " + (removed ? "removed" : "added") + " " + amount + " score, the user now has " + updatedScore + " score.")));

            LeaderboardService.getInstance().refreshLeaderboard();

            return ApplicationService
                    .getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.INFORMATION.getColor())
                    .setTitle("Score-Management")
                    .setDescription(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getMentionTag() +
                            ", the user " + user.getMentionTag() + " now has " + updatedScore + " " + carryType.get().getDisplayName() + "-score.\nYou " + (removed ? "removed" : "added") + " " + amount + " of that score.");
        }));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to manage score.")
                .setRequired(true)
                .build();

        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of score to add/remove.")
                .setLongMaxValue(10000L)
                .setLongMinValue(0L)
                .setRequired(true)
                .build();

        List<SlashCommandOption> manageScoreSubOptions = Arrays.asList(userOption,
                CarryTypeCommand.getCarryTypeOption(), amountOption);

        SlashCommandOption addCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("add")
                .setDescription("Add score.")
                .setOptions(manageScoreSubOptions)
                .build();

        SlashCommandOption removeCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("remove")
                .setDescription("Remove score.")
                .setOptions(manageScoreSubOptions)
                .build();

        return Arrays.asList(addCommand, removeCommand);
    }
}