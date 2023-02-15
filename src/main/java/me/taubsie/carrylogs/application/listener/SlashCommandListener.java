package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.exceptions.*;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.start.StartBot;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.awt.*;
import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class SlashCommandListener implements SlashCommandCreateListener
{
    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        Optional<Map.Entry<SlashCommand, Command>> createdCommand = StartBot.getInstance()
                .getSlashCommandMap()
                .entrySet()
                .parallelStream()
                .filter(entry -> entry.getKey().getName().equalsIgnoreCase(slashCommandCreateEvent.getSlashCommandInteraction().getCommandName()))
                .filter(entry -> (entry.getKey().isGlobalApplicationCommand())
                        || (entry.getKey().isServerApplicationCommand()
                        && slashCommandCreateEvent.getSlashCommandInteraction().getServer().isPresent()
                        && entry.getKey().getServerId().isPresent()
                        && entry.getKey().getServerId().get() == slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId()))
                .findAny();

        try
        {
            if (createdCommand.isEmpty())
            {
                throw new UnknownCommandException();
            }

            createdCommand.get().getValue().execute(slashCommandCreateEvent);
        }
        catch (CommandExecutionException commandExecutionException)
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .addEmbed(ApplicationService.getInstance()
                            .getEmbed()
                            .setTitle("Error")
                            .setDescription(commandExecutionException.getMessage())
                            .setColor(new Color(255, 0, 0 /*TODO color*/)))
                    .respond();
        }
    }
}