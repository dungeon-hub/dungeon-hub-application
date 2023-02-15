package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;

@CommandParameters(name = "score-help",
                   description = "Show an explanation about how score works.",
                   enabledInDms = true)
public class ScoreHelpCommand extends Command
{
    @Override
    public void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle("Carry Score")
                        .setDescription("You gain score based on the carries that you do.\n" +
                                "Different types of carries give you certain score:")
                        .setColor(new Color(165, 23, 112 /*TODO color*/))
                        .addField(
                                "Dungeons",
                                "Completion - 2\n" +
                                        "S / S+ - 3"
                        )
                        .addField(
                                "Master Mode Dungeons",
                                "Any - 3"
                        )
                        .addField(
                                "Slayer",
                                """
                                        T2 - 1
                                        T3 - 2
                                        T4 - 3"""
                        ))
                .respond();
    }
}