package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.service.ApplicationService;
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
        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() ->
                ApplicationService.getInstance().getPlayerDataEmbed(getStringOption("ign"))
        ));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption slashCommandOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("ign")
                .setDescription("The IGN of the player")
                .setMinLength(2L)
                .setRequired(true)
                .build();

        return List.of(slashCommandOption);
    }
}