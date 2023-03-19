package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommand;

import java.util.Optional;

@CommandParameters(name = "help",
        enabledInDms = true,
        description = "List of available commands.")
public class HelpCommand extends Command {
    @Override
    public void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Optional<SlashCommand> logCommand = ApplicationClassLoaderService.getInstance().getSlashCommand("log", getServer());
        Optional<SlashCommand> scoreCommand = ApplicationClassLoaderService.getInstance().getSlashCommand("score", getServer());
        Optional<SlashCommand> scoreHelpCommand = ApplicationClassLoaderService.getInstance().getSlashCommand("score-help", getServer());
        Optional<SlashCommand> calcPriceCommand = ApplicationClassLoaderService.getInstance().getSlashCommand("calc-price", getServer());

        respondEphemeral(getEmbed()
                .setTitle("**Bot Usage:**")
                .setDescription("""
                        This bot uses slash commands, in order to use it you must have your discord client updated (No need to worry if you're on desktop).

                        Type out %log% **in the ticket** , you will then see a prompt showing you all you have to input.

                         **Usage:** `/log amount:NUMBER carry-type:Completion/S/S+/Tier 2/Tier 3/Tier 4`
                         
                         To see the **score** you have gained, you can use %score%
                         To learn more about **score**, use %score-help%
                         
                         To calculate the price of carries, use %calc-price%"""
                        .replace("%log%", logCommand.map(SlashCommand::getMentionTag).orElse("`/log`"))
                        .replace("%score%", scoreCommand.map(SlashCommand::getMentionTag).orElse("`/score`"))
                        .replace("%score-help%", scoreHelpCommand.map(SlashCommand::getMentionTag).orElse("`/score-help`"))
                        .replace("%calc-price%", calcPriceCommand.map(SlashCommand::getMentionTag).orElse("`/calc-price`")))
                .setColor(EmbedColor.INFORMATION.getColor()));
    }
}