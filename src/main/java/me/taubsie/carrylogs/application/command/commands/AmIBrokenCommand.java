package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;

@CommandParameters(name = "amibroken", description = "Tells you if the bot is currently broken.", enabledInDms = true)
public class AmIBrokenCommand extends Command {
    //TODO update
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        EmbedBuilder response = ApplicationService.getInstance()
                .getEmbed()
                .setColor(Color.YELLOW)
                .setDescription("""
                        There is currently no feature that is known to be broken, but revamps of some code has happened. This can cause issues with:
                        > Configs
                        > Strikes (unfinished)""");

        respondEphemeral(response);
    }
}