package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import me.taubsie.carrylogs.application.service.PermissionService;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "manage-score",
                   description = "Use this to manage the score of carriers.",
                   enabledForPermissions = {PermissionType.MANAGE_MESSAGES})
public class ManageScoreCommand extends Command
{
    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        String[] validTypes = new String[]{"dungeons", "slayer", "kuudra"};

        Server server = getServer();

        if (!PermissionService.getInstance().mayManageScore(slashCommandCreateEvent.getSlashCommandInteraction().getUser(),
                server))
        {
            throw new MissingPermissionException();
        }

        Optional<SlashCommandInteractionOption> addRemoveOption =
                slashCommandCreateEvent.getSlashCommandInteraction().getOptionByIndex(0);

        if (addRemoveOption.isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setContent("Please either" +
                    " add or remove score.").setFlags(MessageFlag.EPHEMERAL).respond();
            return;
        }

        SlashCommandInteractionOption subCommand = addRemoveOption.get();
        boolean removed = subCommand.getName().equalsIgnoreCase("remove");

        User user = getUserOption(subCommand, "user");

        String scoreType = getStringOption(subCommand, "score-type");

        if (Arrays.stream(validTypes).noneMatch(s -> s.equalsIgnoreCase(scoreType)))
        {
            throw new InvalidOptionException("score-type", "Please enter a valid score-type (" + String.join(", ",
                    validTypes) + ")");
        }

        Long amount = getLongOption(subCommand, "amount");

        long updatedScore = ConnectionService.getInstance().modifyScore(user.getId(), scoreType, removed ? -amount :
                amount);

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(ApplicationService
                        .getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.INFORMATION.getColor())
                        .setTitle("Score-Management")
                        .setDescription(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getMentionTag() + ", the user " + user.getMentionTag() + " now has " + updatedScore + " " + scoreType + "-score.\nYou " + (removed ? "removed" : "added") + " " + amount + " of that score."))
                .respond();

        Optional<ServerTextChannel> logs =
                server.getTextChannelById(IdList.SCORE_LOGS_CHANNEL.getLocalId(server.getId()));

        logs.ifPresent(serverTextChannel ->
                serverTextChannel.sendMessage(ApplicationService
                        .getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.INFORMATION.getColor())
                        .setTitle("Score-Management")
                        .setDescription(slashCommandCreateEvent.getSlashCommandInteraction().getUser().getMentionTag() + " edited the " + scoreType + "-score of " + user.getMentionTag() + ".\nThey " + (removed ? "removed" : "added") + " " + amount + " score, the user now has " + updatedScore + " score.")));

        LeaderboardService.getInstance().refreshLeaderboard();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions()
    {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to manage score.")
                .setRequired(true)
                .build();

        SlashCommandOption scoreTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("score-type")
                .setDescription("The type of score to manage.")
                .setRequired(true)
                .addChoice("dungeons", "dungeons")
                .addChoice("slayer", "slayer")
                .addChoice("kuudra", "kuudra")
                .build();

        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of score to add/remove.")
                .setLongMaxValue(10000L)
                .setLongMinValue(0L)
                .setRequired(true)
                .build();

        List<SlashCommandOption> manageScoreSubOptions = Arrays.asList(userOption, scoreTypeOption, amountOption);

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