package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

@CommandParameters(name = "help",
        enabledInDms = true,
        description = "List of available commands.")
public class HelpCommand extends Command {
    @Override
    public void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
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
                .setColor(EmbedColor.INFORMATION.getColor()));
    }
}