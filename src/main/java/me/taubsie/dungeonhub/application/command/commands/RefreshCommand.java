package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.AuthorizationConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.MissingPermissionException;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.application.service.MessagesService;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;

import java.util.List;

@CommandParameters(name = "refresh",
        description = "Refreshes some data from the bot.",
        enabledForPermissions = {PermissionType.MANAGE_MESSAGES})
public class RefreshCommand extends Command {
    private static final List<String> choices = List.of("leaderboard", "price-message", "backend");

    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L, 764326796736856066L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String option = getStringOption("type");

        switch (option.toLowerCase()) {
            case "leaderboard" -> {
                InteractionOriginalResponseUpdater updater = slashCommandCreateEvent
                        .getSlashCommandInteraction()
                        .respondLater(true)
                        .join();

                //TODO fix
                /*if (!LeaderboardService.getInstance().refreshLeaderboard()) {
                    updater.addEmbed(getEmbed()
                                    .setColor(EmbedColor.NEGATIVE.getColor())
                                    .setDescription("Leaderboard refresh is on cooldown.\n" +
                                            "Please try again <t:" + LeaderboardService.getInstance().getNextPossibleRefresh() + ":R>."))
                            .update();
                    return;
                }*/

                updater.addEmbed(getEmbed()
                                .setColor(EmbedColor.POSITIVE.getColor())
                                .setDescription("Leaderboard refresh started."))
                        .update();
            }
            case "price-message" -> {
                InteractionOriginalResponseUpdater updater = slashCommandCreateEvent
                        .getSlashCommandInteraction()
                        .respondLater(true)
                        .join();

                slashCommandCreateEvent.getSlashCommandInteraction()
                        .getServer()
                        .ifPresent(server -> MessagesService.getInstance().refreshPriceMessages(server));

                updater.addEmbed(getEmbed()
                                .setColor(EmbedColor.POSITIVE.getColor())
                                .setDescription("Prices refreshed!"))
                        .update();
            }
            case "backend" -> {
                if (!getUser().isBotOwnerOrTeamMember()) {
                    throw new MissingPermissionException();
                }

                InteractionOriginalResponseUpdater updater = slashCommandCreateEvent
                        .getSlashCommandInteraction()
                        .respondLater(true)
                        .join();

                AuthorizationConnection.getInstance().loadToken();

                updater.addEmbed(getEmbed()
                                .setColor(EmbedColor.POSITIVE.getColor())
                                .setDescription("Token should have been reloaded!"))
                        .update();
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