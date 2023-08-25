package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadEmbedException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.component.ActionRowBuilder;
import org.javacord.api.entity.message.component.ButtonBuilder;
import org.javacord.api.entity.message.component.ButtonStyle;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "playerdata", enabledInDms = true, description = "Displays the data for the given user user.")
public class PlayerDataCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() -> {
                    EmbedBuilder playerDataEmbed;
                    try {
                        playerDataEmbed = ApplicationService.getInstance().getPlayerDataEmbed(ign);
                    } catch (FailedToLoadEmbedException failedToLoadEmbedException) {
                        playerDataEmbed = failedToLoadEmbedException.getEmbed();
                    }
                    return playerDataEmbed;
                }
        ), new ActionRowBuilder().addComponents(
                new ButtonBuilder().setStyle(ButtonStyle.LINK)
                        .setUrl("https://sky.shiiyu.moe/stats/" + ign)
                        .setLabel("SkyCrypt")
                        .build()
        ).build());
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