package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.start.StartBot;
import me.taubsie.carrylogs.CarryInformation;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.awt.*;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class SlashCommandListener implements SlashCommandCreateListener
{
    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        switch (slashCommandCreateEvent.getSlashCommandInteraction().getCommandName().toLowerCase())
        {
            case "log" -> log(slashCommandCreateEvent);
            case "score" -> carryCount(slashCommandCreateEvent);
            case "score-help" -> showScoreHelp(slashCommandCreateEvent);
            case "help" -> showHelp(slashCommandCreateEvent);
            default -> slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Unknown command.")
                    .respond();
        }
    }

    private void showScoreHelp(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle("Carry Score")
                        .setDescription("You gain score based on the carries that you do.\n" +
                                "Different types of carries give you certain score:")
                        .setColor(new Color(165, 23, 112 /*TODO color*/))
                        .addField(
                                "Dungeons",
                                "Completion - 2\n" +
                                        "S / S+ - 3"
                        )
                        .addField(
                                "Master Mode Dungeons",
                                "Any - 3"
                        )
                        .addField(
                                "Slayer",
                                """
                                        T2 - 1
                                        T3 - 2
                                        T4 - 3"""
                        ))
                .respond();
    }

    private void carryCount(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        User userToCheck = slashCommandCreateEvent.getSlashCommandInteraction().getUser();

        if (slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("user").isPresent()
                && slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("user").get().getUserValue().isPresent())
        {
            userToCheck = slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("user").get().getUserValue().get();
        }

        Map<String, Long> carryCount = ConnectionService.getInstance().countScore(userToCheck.getId());

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle((userToCheck.getId() != slashCommandCreateEvent.getSlashCommandInteraction().getUser().getId()
                                && slashCommandCreateEvent.getSlashCommandInteraction().getServer().isPresent())
                                ? userToCheck.getDisplayName(slashCommandCreateEvent.getSlashCommandInteraction().getServer().get()) + "'s score:"
                                : "Your score:")
                        .setColor(new Color(165, 23, 112 /*TODO color*/))
                        .addInlineField("Dungeon-Score:", String.valueOf(carryCount.get("dungeon")))
                        .addInlineField("Slayer-Score:", String.valueOf(carryCount.get("slayer"))))
                .respond();
    }

    private void log(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        if (slashCommandCreateEvent.getSlashCommandInteraction().getServer().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().isEmpty()
                || !IdList.isCarryCategory(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().get().getId(), slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Please use this in a carry-ticket.")
                    .respond()
                    .join();
            return;
        }

        if (StartBot.getInstance().getCarryInformation().containsKey(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getId()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Someone is already logging this carry.")
                    .respond()
                    .join();
            return;
        }

        Optional<SlashCommandInteractionOption> amountOfCarries = slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("amount");

        if (amountOfCarries.isEmpty() || amountOfCarries.get().getLongValue().isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Incorrect usage: No amount of carries specified.")
                    .respond()
                    .join();
            return;
        }

        Optional<SlashCommandInteractionOption> carryType = slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("carry-type");

        if (carryType.isEmpty()
                || carryType.get().getStringValue().isEmpty()
                || !ApplicationService.getInstance().isCarryType(carryType.get().getStringValue().get()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Incorrect usage: No type of carry specified.")
                    .respond()
                    .join();
            return;
        }

        Message firstMessage = slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getMessagesAsStream().reduce((message, message2) -> message2).orElse(null);

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
                        .addInlineField("Number of carries", String.valueOf(amountOfCarries.get().getLongValue().get()))
                        .addInlineField("Type of carry", carryType.get().getStringValue().get())
                        .addInlineField("Player", carried.getMentionTag())
                        .addInlineField("Carrier", carrier.getMentionTag()))
                .addComponents(ActionRow.of(Button.success("send_log", "Confirm"), Button.danger("discard", "Cancel")))
                .respond().join();

        CarryInformation carryInformation = new CarryInformation(
                time,
                amountOfCarries.get().getLongValue().get(),
                IdList.getCarryCategory(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().get().getId(), slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId()).getCarryType().name(),
                carryType.get().getStringValue().get(),
                carried.getId(),
                carrier.getId()
        );

        StartBot.getInstance().getCarryInformation().put(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getId(), carryInformation);
    }

    private void showHelp(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle("**Bot Usage:**")
                        .setDescription("""
                                This bot uses slash commands, in order to use it you must have your discord client updated (No need to worry if you're on desktop).

                                Type out `/log` **in the ticket** , you will then see a prompt showing you all you have to input.

                                 **Usage:** `/log amount:NUMBER carry-type:Completion/S/S+/Tier 2/Tier 3/Tier 4`
                                 
                                 To see the **score** you have gained, you can use `/score`
                                 To learn more about **score**, use `/score-help`""")
                        .setColor(/*TODO*/ new Color(255, 255, 255)))
                .respond().join();
    }
}