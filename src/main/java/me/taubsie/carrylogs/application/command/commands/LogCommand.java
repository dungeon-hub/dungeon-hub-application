package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryDifficulty;
import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.dungeonhub.common.CarryTier;
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
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "log",
        description = "Use this to log your carries.")
public class LogCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        TextChannel channel = getChannel();

        Optional<CarryTier> carryTier = channel.asCategorizable()
                .flatMap(Categorizable::getCategory)
                .flatMap(channelCategory -> DungeonHubConnection.getInstance()
                        .getCarryTierFromCategory(getServer().getId(), channelCategory.getId()));

        if(carryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Please use this in a carry-ticket. If this is one, tell the administrators to do `/setup`!";
                }
            };
        }

        if(BotStarter.getInstance().getCarryInformation().containsKey(channel.getId())) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Someone is already logging this carry, please wait a bit.";
                }
            };
        }

        Long amountOfCarries = getLongOption(slashCommandCreateEvent.getSlashCommandInteraction(), "amount");

        Optional<CarryDifficulty> carryDifficulty = DungeonHubConnection.getInstance()
                .loadCarryDifficulty(carryTier.get(), getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), "carry-difficulty"));

        if(carryDifficulty.isEmpty()) {
            throw new InvalidOptionException("carry-difficulty", carryDifficulty + " is no valid type.");
        }

        Optional<Message> firstMessage = channel.getMessagesAsStream().reduce((message, message2) -> message2);

        if(firstMessage.isEmpty() || firstMessage.get().getMentionedUsers().isEmpty()) {
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't retrieve bot message, so this ticket can't be logged. Please report this.";
                }
            };
        }

        Instant time = Instant.now();
        User carried = firstMessage.get().getMentionedUsers().get(0);
        User carrier = slashCommandCreateEvent.getSlashCommandInteraction().getUser();

        CarryInformation carryInformation = new CarryInformation(
                time,
                amountOfCarries,
                carryDifficulty.get(),
                carried.getId(),
                carrier.getId()
        );

        //TODO method in ApplicationService
        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed(time)
                        .setTitle("Are you sure that you want to log this?")
                        .setColor(EmbedColor.INFORMATION.getColor())
                        .addInlineField("Number of carries", String.valueOf(amountOfCarries))
                        .addInlineField("Type of carry", carryDifficulty.get().toString())
                        .addInlineField("Player", carried.getMentionTag())
                        .addInlineField("Carrier", carrier.getMentionTag()))
                .addComponents(ActionRow.of(Button.success("send_log", "Confirm"),
                        Button.danger("discard", "Cancel")))
                .respond().join();

        BotStarter.getInstance().getCarryInformation().put(channel.getId(), carryInformation);
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