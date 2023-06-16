package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandParameters(name = "score", description = "Use this to count your or another user's carries.")
public class ScoreCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        User userToCheck;
        try {
            userToCheck = getUserOption("user");
        }
        catch (InvalidOptionException invalidOptionException) {
            userToCheck = getUser();
        }

        Map<String, Long> scoreCount = DungeonHubConnection.getInstance().countScore(getServer().getId(),
                userToCheck.getId());

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getScoreCountMessage(userToCheck, getUser(), getServer(), scoreCount))
                .respond();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to check the carries for.")
                .setRequired(false)
                .build();

        return Collections.singletonList(userOption);
    }
}