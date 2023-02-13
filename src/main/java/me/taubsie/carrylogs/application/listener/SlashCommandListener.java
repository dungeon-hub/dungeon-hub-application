package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.service.ProfileModerationService;
import me.taubsie.carrylogs.application.start.StartBot;
import me.taubsie.carrylogs.CarryInformation;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class SlashCommandListener implements SlashCommandCreateListener
{
    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        if (slashCommandCreateEvent.getSlashCommandInteraction().getServer().isEmpty()
                || IdList.SERVER.getLocalId(slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId()) != slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId())
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("You aren't allowed to use this here!").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        try
        {
            switch (slashCommandCreateEvent.getSlashCommandInteraction().getCommandName().toLowerCase())
            {
                case "log" -> log(slashCommandCreateEvent);
                case "score" -> carryCount(slashCommandCreateEvent);
                case "manage-score" -> manageScore(slashCommandCreateEvent);
                case "score-help" -> showScoreHelp(slashCommandCreateEvent);
                case "rolesync" -> roleSync(slashCommandCreateEvent);
                case "userscan" -> userScan(slashCommandCreateEvent);
                case "help" -> showHelp(slashCommandCreateEvent);
                default -> slashCommandCreateEvent.getSlashCommandInteraction()
                        .createImmediateResponder()
                        .setFlags(MessageFlag.EPHEMERAL)
                        .setContent("Unknown command.")
                        .respond();
            }
        }
        catch (InvalidOptionException invalidOptionException)
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .addEmbed(ApplicationService.getInstance()
                            .getEmbed()
                            .setTitle("Error")
                            .setDescription(invalidOptionException.getMessage())
                            .setColor(new Color(255, 0, 0 /*TODO color*/)))
                    .respond();
        }
    }

    private void userScan(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        if (slashCommandCreateEvent.getSlashCommandInteraction().getServer().isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("Please use this on a server").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        Server server = slashCommandCreateEvent.getSlashCommandInteraction().getServer().get();
        Map<User, String> result = new HashMap<>();

        for (User user : server.getMembers())
        {
            if (!user.isBot())
            {
                String checkResult = ProfileModerationService.getInstance().checkUserName(user.getName());
                if (checkResult != null)
                {
                    result.put(user, checkResult);
                }
            }
        }

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService.getInstance().getEmbed().setDescription(result.entrySet()
                        .stream()
                        .map(userStringEntry ->
                                userStringEntry.getKey().getMentionTag() + " - " + userStringEntry.getValue())
                        .collect(Collectors.joining("\n"))))
                .respond();
    }

    private void roleSync(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        if (!slashCommandCreateEvent.getSlashCommandInteraction().getUser().isBotOwnerOrTeamMember())
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("Not allowed to use that!").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }


    }

    private void manageScore(SlashCommandCreateEvent slashCommandCreateEvent) throws InvalidOptionException
    {
        String[] validTypes = new String[]{"dungeons", "slayer"};

        Optional<Server> server = slashCommandCreateEvent.getSlashCommandInteraction().getServer();

        if (server.isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("Use this on a server please!").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        if (!StartBot.getInstance().mayManageScore(slashCommandCreateEvent.getSlashCommandInteraction().getUser(), server.get()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("You aren't allowed to do that!").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        Optional<SlashCommandInteractionOption> addRemoveOption = slashCommandCreateEvent.getSlashCommandInteraction().getOptionByIndex(0);

        if (addRemoveOption.isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("Please either add or remove score.").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        SlashCommandInteractionOption subCommand = addRemoveOption.get();
        boolean removed = subCommand.getName().equalsIgnoreCase("remove");

        User user = ApplicationService.getInstance().getUserOption(subCommand, "user");

        String scoreType = ApplicationService.getInstance().getStringOption(subCommand, "score-type");

        if (Arrays.stream(validTypes).noneMatch(s -> s.equalsIgnoreCase(scoreType)))
        {
            throw new InvalidOptionException("score-type", "Please enter a valid score-type (" + String.join(", ", validTypes) + ")");
        }

        Long amount = ApplicationService.getInstance().getLongOption(subCommand, "amount");

        long updatedScore = ConnectionService.getInstance().modifyScore(user.getId(), scoreType, removed ? -amount : amount);

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService
                        .getInstance()
                        .getEmbed()
                        .setColor(new Color(0, 255, 0 /*TODO*/))
                        .setTitle("Score-Management")
                        .setDescription(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getMentionTag() + ", the user " + user.getMentionTag() + " now has " + updatedScore + " " + scoreType + "-score.\nYou " + (removed ? "removed" : "added") + " " + amount + " of that score."))
                .respond();

        Optional<ServerTextChannel> logs = server.get().getTextChannelById(IdList.SCORE_LOGS_CHANNEL.getLocalId(server.get().getId()));

        logs.ifPresent(serverTextChannel ->
                serverTextChannel.sendMessage(ApplicationService
                        .getInstance()
                        .getEmbed()
                        .setColor(new Color(0, 255, 0 /*TODO*/))
                        .setTitle("Score-Management")
                        .setDescription(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getMentionTag() + " edited the " + scoreType + "-score of " + user.getMentionTag() + ".\nThey " + (removed ? "removed" : "added") + " " + amount + " score, the user now has " + updatedScore + " score.")));
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

        Map<String, Long> scoreCount = ConnectionService.getInstance().countScore(userToCheck.getId());

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
                        .addInlineField("Dungeon-Score:", String.valueOf(scoreCount.get("dungeon")))
                        .addInlineField("Slayer-Score:", String.valueOf(scoreCount.get("slayer"))))
                .respond();
    }

    private void log(SlashCommandCreateEvent slashCommandCreateEvent) throws InvalidOptionException
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

        Long amountOfCarries = ApplicationService.getInstance().getLongOption(slashCommandCreateEvent.getSlashCommandInteraction(), "amount");

        String carryType = ApplicationService.getInstance().getStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), "carry-type");

        if (!ApplicationService.getInstance().isCarryType(carryType))
        {
            throw new InvalidOptionException("carry-type", carryType + " is no valid carry-type.");
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
                        .addInlineField("Number of carries", String.valueOf(amountOfCarries))
                        .addInlineField("Type of carry", carryType)
                        .addInlineField("Player", carried.getMentionTag())
                        .addInlineField("Carrier", carrier.getMentionTag()))
                .addComponents(ActionRow.of(Button.success("send_log", "Confirm"), Button.danger("discard", "Cancel")))
                .respond().join();

        CarryInformation carryInformation = new CarryInformation(
                time,
                amountOfCarries,
                IdList.getCarryCategory(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().get().getId(), slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId()).getCarryType().name(),
                carryType,
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