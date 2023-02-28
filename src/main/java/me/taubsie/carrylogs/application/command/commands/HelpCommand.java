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

                        Type out `/log` **in the ticket** , you will then see a prompt showing you all you have to input.

                         **Usage:** `/log amount:NUMBER carry-type:Completion/S/S+/Tier 2/Tier 3/Tier 4`
                         
                         To see the **score** you have gained, you can use `/score`
                         To learn more about **score**, use `/score-help`""")
                .setColor(EmbedColor.INFORMATION.getColor()));
    }
}