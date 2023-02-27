package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;

import java.awt.*;
import java.util.List;

@CommandParameters(name = "refresh",
        enabledServers = {693263712626278553L, 1023684107877761196L},
        description = "Refreshes some data from the bot.",
        enabledForPermissions = {PermissionType.MANAGE_MESSAGES})
public class RefreshCommand extends Command {
    private static final List<String> choices = List.of("leaderboard");

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String option = getStringOption("type");

        switch(option.toLowerCase()) {
            case "leaderboard" -> {
                if(!LeaderboardService.getInstance().refreshLeaderboard()) {
                    respondEphemeral(getEmbed()
                            .setColor(new Color(255, 0, 0 /*TODO color*/))
                            .setDescription("Leaderboard refresh is on cooldown.\n" +
                                    "Please try again <t:" + LeaderboardService.getInstance().getNextPossibleRefresh() + ":R>."));
                    return;
                }

                respond(getEmbed()
                        .setColor(new Color(255, 255, 255 /*TODO color*/))
                        .setDescription("Leaderboard refresh started."));
            }
            //so that intellij doesn't make this into an if statement, can remove this
            case "" -> {
            }

            default -> respondWithError(new InvalidOptionException("type",
                    "Please enter one of the following: " + String.join(
                            ", ", choices)));
        }
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        return choices.stream()
                .map(s -> new SlashCommandOptionBuilder()
                        .setType(SlashCommandOptionType.SUB_COMMAND)
                        .setName(s)
                        .setDescription("Refresh the " + s + ".")
                        .build())
                .toList();
    }
}