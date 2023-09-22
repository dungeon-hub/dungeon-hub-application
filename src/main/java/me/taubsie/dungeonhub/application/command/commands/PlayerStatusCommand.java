package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "playerstatus", description = "Shows you the current status of a player.", enabledInDms = true)
public class PlayerStatusCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() ->
                ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(Color.GREEN)
                        .setDescription(HypixelConnection.getInstance()
                                .getOnlineStatus(ign)
                                .toString())
        ));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("ign")
                .setDescription("The IGN of the player")
                .setMinLength(2L)
                .setRequired(true)
                .build();

        return List.of(ignOption);
    }
}