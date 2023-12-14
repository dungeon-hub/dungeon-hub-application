package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.classes.HelpDisplay;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.enums.HelpTopic;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.MustBeServerException;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionChoiceBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "help", enabledInDms = true, description = "List of available commands.")
public class HelpCommand extends Command {
    @Override
    public void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        try {
            HelpTopic helpTopic = getEnumOption("topic", HelpTopic.class);

            CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();
            respondLater(completableFuture);

            completableFuture.completeAsync(() -> {
                EmbedBuilder embed = getEmbed().setTitle("**" + helpTopic.getTitle() + "**");

                Server server;
                try {
                    server = getServer();
                }
                catch (MustBeServerException ignored) {
                    server = null;
                }

                HelpDisplay helpDisplay = helpTopic.getDescription().getDescription(getUser(), server);
                embed.setDescription(helpDisplay.description());
                embed.setColor(helpDisplay.embedColor().getColor());
                helpDisplay.fields().forEach(embed::addField);

                return DelayedResponse.fromEmbedWithAttachment(embed, helpDisplay.attachmentUrl());
            });
        }
        catch (InvalidOptionException ignored) {
            respondEphemeral(getEmbed()
                    .setTitle("**Bot Usage:**")
                    .setDescription("""
                            This bot uses slash commands, in order to use it you must have your discord client updated (No need to worry if you're on desktop).
                                                    
                            **Usage:**\s
                            `/log amount:NUMBER carry-type:Completion/S/S+/Tier 2/Tier 3/Tier 4` - Run this inside the ticket you are logging to log your carries and earn score.
                            `/score` - Displays your current score.
                            `/score-help` - Displays more information about the score system.
                            `/calc-price type:TYPE tier:TIER amount:AMOUNT` - Calculates the price of carries.
                            `/leaderboard leaderboard:TYPE` - Shows a leaderboard containing either the current or the all-time score.""")
                    .setColor(EmbedColor.DEFAULT.getColor()));
        }
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption topicOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("topic")
                .setDescription("Select what topic you need help with.")
                .setChoices(Arrays.stream(HelpTopic.values())
                        .map(helpTopic -> new SlashCommandOptionChoiceBuilder()
                                .setName(helpTopic.getName())
                                .setValue(helpTopic.getName())
                                .build())
                        .toList()
                )
                .setRequired(false)
                .build();

        return List.of(topicOption);
    }
}