package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.QueueConnection;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.enums.QueueStep;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueCreationModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import org.javacord.api.entity.channel.Categorizable;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "log",
        description = "Use this to log your carries.")
public class LogCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        TextChannel channel = getChannel();

        Optional<CarryTierModel> carryTier = channel.asCategorizable()
                .flatMap(Categorizable::getCategory)
                .flatMap(channelCategory -> DiscordServerConnection.getInstance()
                        .getCarryTierFromCategory(getServer().getId(), channelCategory.getId()));

        if (carryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Please use this in a carry-ticket. If this is one, tell the administrators to do `/setup`!");
        }

        if (QueueConnection.getInstance()
                .getCarryQueueByRelatedIdAndQueueStep(channel.getId(), QueueStep.CONFIRMATION).stream()
                .flatMap(Collection::stream)
                .findFirst().isPresent()) {
            //TODO custom class
            throw new CommandExecutionException("Someone is already logging this carry.\n" +
                    "If you think this is a mistake, please report this to <@356134481452597250>.");
        }

        Long amountOfCarries = getLongOption(slashCommandCreateEvent.getSlashCommandInteraction(), "amount");

        Optional<CarryDifficultyModel> carryDifficulty = CarryDifficultyConnection.getInstance(carryTier.get())
                .getByIdentifier(getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(),
                        "carry-difficulty"));

        if (carryDifficulty.isEmpty()) {
            throw new InvalidOptionException("carry-difficulty", carryDifficulty + " is no valid type.");
        }

        Optional<Message> firstMessage = channel.getMessagesAsStream().reduce((message, message2) -> message2);

        if (firstMessage.isEmpty() || firstMessage.get().getMentionedUsers().isEmpty()) {
            throw new CommandExecutionException("Couldn't retrieve bot message, so this ticket can't be logged. Please report this.");
        }

        Instant time = Instant.now();
        User carried = firstMessage.get().getMentionedUsers().get(0);
        User carrier = slashCommandCreateEvent.getSlashCommandInteraction().getUser();

        CarryQueueCreationModel creationModel = new CarryQueueCreationModel()
                .setQueueStep(QueueStep.CONFIRMATION)
                .setTime(time)
                .setAmount(amountOfCarries)
                .setPlayer(carried.getId())
                .setCarrier(carrier.getId())
                .setRelationId(channel.getId());

        Optional<CarryQueueModel> carryQueueModel = QueueConnection.getInstance()
                .addNewQueue(carryDifficulty.get(), creationModel);

        if (carryQueueModel.isEmpty()) {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .addEmbed(
                            ApplicationService.getInstance()
                                    .getErrorEmbed()
                                    .setTitle("Unable to log this. Please contact an administrator of this bot.")
                    ).respond();
            return;
        }

        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService.getInstance()
                        .loadEmbedFromCarryQueue(carryQueueModel.get())
                        .setTitle("Are you sure that you want to log this?"))
                .addComponents(ActionRow.of(Button.success("send_log", "Confirm"),
                        Button.danger("discard", "Cancel")))
                .respond().join();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption carryAmountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you did.")
                .setLongMaxValue(200)
                .setLongMinValue(1L)
                .setRequired(true)
                .build();

        SlashCommandOption carryDifficultyOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-difficulty")
                .setDescription("The difficulty of the carry.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        return Arrays.asList(carryAmountOption, carryDifficultyOption);
    }
}