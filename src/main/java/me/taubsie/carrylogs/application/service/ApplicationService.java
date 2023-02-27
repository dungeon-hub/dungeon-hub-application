package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.config.ConfigProperty;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;
import java.time.Instant;
import java.util.*;

public class ApplicationService {
    private static ApplicationService instance;

    public static ApplicationService getInstance() {
        if(instance == null) {
            instance = new ApplicationService();
        }

        return instance;
    }

    public EmbedBuilder getEmbed() {
        return getEmbed(Instant.now());
    }

    public EmbedBuilder getEmbed(Instant time) {
        return new EmbedBuilder()
                .setTimestamp(time)
                .setFooter("discord.gg/dungeons • made by Taubsie#0911");
    }

    public boolean isCarryType(String carryType) {
        return switch(carryType) {
            case "Completion",
                    "S",
                    "S+",
                    "Floor 1",
                    "Floor 2",
                    "Floor 3",
                    "Floor 4",
                    "Floor 5",
                    "Floor 6",
                    "Floor 7",
                    "Basic",
                    "Hot",
                    "Burning",
                    "Fiery",
                    "Infernal",
                    "Tier 2",
                    "Tier 3",
                    "Tier 4" -> true;
            default -> false;
        };
    }

    public DiscordApiBuilder getApiBuilder() {
        return new DiscordApiBuilder()
                .setToken(ConfigProperty.DISCORD_BOT_TOKEN.getValue())
                .setAllNonPrivilegedIntents()
                .addIntents(Intent.MESSAGE_CONTENT, Intent.GUILD_MEMBERS);
    }

    public void respondWithError(SlashCommandCreateEvent slashCommandCreateEvent,
                                 CommandExecutionException commandExecutionException) {
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

    public Optional<Command> getCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        return ApplicationClassLoaderService.getInstance().getCommand(
                slashCommandCreateEvent.getSlashCommandInteraction().getCommandName(),
                slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElse(null)
        );
    }
}