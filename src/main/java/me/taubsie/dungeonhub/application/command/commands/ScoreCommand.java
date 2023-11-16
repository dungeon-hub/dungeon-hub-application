package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ServerConnection;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.common.model.server.ServerModel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CommandParameters(name = "score", description = "Use this to see the score of yourself or another user.")
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

        List<ScoreModel> scores = ServerConnection.getInstance()
                .getScores(new ServerModel(getServer().getId()), userToCheck.getId())
                .orElse(new ArrayList<>());

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getScoreCountMessage(userToCheck, getUser(), getServer(), scores))
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