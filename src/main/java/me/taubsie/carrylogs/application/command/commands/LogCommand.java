package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.start.BotStarter;
import org.javacord.api.entity.channel.Categorizable;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.TextChannel;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "log",
        description = "Use this to log your carries.")
public class LogCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();

        Optional<TextChannel> channel = slashCommandCreateEvent.getSlashCommandInteraction().getChannel();

        Optional<ChannelCategory> category = channel.flatMap(Channel::asCategorizable)
                .flatMap(Categorizable::getCategory);

        if(channel.isEmpty()
                || category.isEmpty()
                || !IdList.isCarryCategory(category.get().getId(), server.getId())) {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Please use this in a carry-ticket.")
                    .respond()
                    .join();
            return;
        }

        if(BotStarter.getInstance().getCarryInformation().containsKey(channel.get().getId())) {
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

        String carryTier =
                getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), "carry-tier");

        if(ApplicationService.getInstance().isInvalidCarryTier(carryTier)) {
            throw new InvalidOptionException("carry-tier", carryTier + " is no valid type.");
        }

        Message firstMessage = channel.get().getMessagesAsStream().reduce((message, message2) -> message2).orElse(null);

        if(firstMessage == null || firstMessage.getMentionedUsers().isEmpty()) {
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
                        .setColor(EmbedColor.INFORMATION.getColor())
                        .addInlineField("Number of carries", String.valueOf(amountOfCarries))
                        .addInlineField("Type of carry", carryTier)
                        .addInlineField("Player", carried.getMentionTag())
                        .addInlineField("Carrier", carrier.getMentionTag()))
                .addComponents(ActionRow.of(org.javacord.api.entity.message.component.Button.success("send_log",
                        "Confirm"), Button.danger("discard", "Cancel")))
                .respond().join();

        IdList carryCategory = IdList.getCarryCategory(category.get().getId(), server.getId());

        CarryInformation carryInformation = new CarryInformation(
                time,
                amountOfCarries,
                carryCategory != null ? carryCategory.getCarryType().name() : null,
                carryTier,
                carried.getId(),
                carrier.getId()
        );

        BotStarter.getInstance().getCarryInformation().put(channel.get().getId(), carryInformation);
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

        SlashCommandOption carryTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-tier")
                .setDescription("The type of the carry.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        return Arrays.asList(carryAmountOption, carryTypeOption);
    }
}