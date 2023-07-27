package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.service.ApplicationService;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;

@CommandParameters(name = "setup", description = "Shows you how to setup the bot.", enabledInDms = true)
public class SetupCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        EmbedBuilder response = ApplicationService.getInstance()
                .getEmbed()
                .setColor(Color.YELLOW)
                .setDescription("""
                        > Setup the bot!
                        > (command is unfinished)
                        """);
        respondEphemeral(response);
    }
}