package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;

@CommandParameters(name = "amibroken", description = "Tells you if the bot is currently broken.", enabledInDms = true)
public class AmIBrokenCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        EmbedBuilder response = ApplicationService.getInstance()
                .getEmbed()
                .setColor(Color.YELLOW)
                .setDescription("""
                        > Strikes (unfinished)""");
            // probably try running each of the commands and see if they throw exceptions to check if it's broken?
            // still need to read through more code not sure
        respondEphemeral(response);
    }
}