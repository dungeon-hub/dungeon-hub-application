package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;

import java.util.List;

@CommandParameters(name = "refresh",
        description = "Refreshes some data from the bot.",
        enabledForPermissions = {PermissionType.MANAGE_MESSAGES})
public class RefreshCommand extends Command {
    private static final List<String> choices = List.of("leaderboard");

    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String option = getStringOption("type");

        switch(option.toLowerCase()) {
            case "leaderboard" -> {
                InteractionOriginalResponseUpdater updater = slashCommandCreateEvent
                        .getSlashCommandInteraction()
                        .respondLater(true)
                        .join();

                if(!LeaderboardService.getInstance().refreshLeaderboard()) {
                    updater.addEmbed(getEmbed()
                                    .setColor(EmbedColor.NEGATIVE.getColor())
                                    .setDescription("Leaderboard refresh is on cooldown.\n" +
                                            "Please try again <t:" + LeaderboardService.getInstance().getNextPossibleRefresh() + ":R>."))
                            .update();
                    return;
                }

                updater.addEmbed(getEmbed()
                                .setColor(EmbedColor.POSITIVE.getColor())
                                .setDescription("Leaderboard refresh started."))
                        .update();
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
        SlashCommandOptionBuilder typeOptionBuilder = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("Select what to refresh.")
                .setRequired(true);

        choices.forEach(s -> typeOptionBuilder.addChoice(s, s));

        return List.of(typeOptionBuilder.build());
    }
}