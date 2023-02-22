package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.CarryInformation;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.start.BotStarter;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@CommandParameters(name = "log",
                   description = "Use this to log your carries.",
                   enabledServers = {693263712626278553L, 1023684107877761196L})
public class LogCommand extends Command
{
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        Server server = getServer(slashCommandCreateEvent.getInteraction());

        if (slashCommandCreateEvent.getSlashCommandInteraction().getChannel().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().isEmpty()
                || !IdList.isCarryCategory(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().get().getId(), server.getId()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Please use this in a carry-ticket.")
                    .respond()
                    .join();
            return;
        }

        if (BotStarter.getInstance().getCarryInformation().containsKey(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getId()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Someone is already logging this carry.")
                    .respond()
                    .join();
            return;
        }

        Long amountOfCarries =
                getLongOption(slashCommandCreateEvent.getSlashCommandInteraction(), "amount");

        String carryType =
                getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), "carry-type");

        if (!ApplicationService.getInstance().isCarryType(carryType))
        {
            throw new InvalidOptionException("carry-type", carryType + " is no valid carry-type.");
        }

        Message firstMessage =
                slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getMessagesAsStream().reduce((message, message2) -> message2).orElse(null);

        if (firstMessage == null || firstMessage.getMentionedUsers().isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Couldn't retrieve bot message. Please report this.")
                    .respond()
                    .join();
            return;
        }

        Instant time = Instant.now();
        User carried = firstMessage.getMentionedUsers().get(0);
        User carrier = slashCommandCreateEvent.getSlashCommandInteraction().getUser();

        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed(time)
                        .setTitle("Are you sure that you want to log this?")
                        .setColor(new Color(/* TODO green */ 165, 23, 112))
                        .addInlineField("Number of carries", String.valueOf(amountOfCarries))
                        .addInlineField("Type of carry", carryType)
                        .addInlineField("Player", carried.getMentionTag())
                        .addInlineField("Carrier", carrier.getMentionTag()))
                .addComponents(ActionRow.of(org.javacord.api.entity.message.component.Button.success("send_log", "Confirm"), Button.danger("discard", "Cancel")))
                .respond().join();

        CarryInformation carryInformation = new CarryInformation(
                time,
                amountOfCarries,
                IdList.getCarryCategory(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().get().getId(), slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId()).getCarryType().name(),
                carryType,
                carried.getId(),
                carrier.getId()
        );

        BotStarter.getInstance().getCarryInformation().put(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getId(), carryInformation);
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions()
    {
        SlashCommandOption carryAmountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you did.")
                .setLongMaxValue(200)
                .setLongMinValue(0L)
                .setRequired(true)
                .build();

        SlashCommandOption carryTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-type")
                .setDescription("The type of the carry.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        return Arrays.asList(carryAmountOption, carryTypeOption);
    }
}