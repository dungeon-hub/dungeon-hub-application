package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadEmbedException;
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.component.ActionRowBuilder;
import org.javacord.api.entity.message.component.ButtonBuilder;
import org.javacord.api.entity.message.component.ButtonStyle;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "playerdata", enabledInDms = true, description = "Displays the data for the given user user.")
public class PlayerDataCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger(PlayerDataCommand.class);

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        respondLater(new CompletableFuture<DelayedResponse>().completeAsync(() -> {
            DelayedResponse.DelayedResponseBuilder delayedResponseBuilder = DelayedResponse.builder();

            try {
                delayedResponseBuilder.setEmbed(ApplicationService.getInstance().getPlayerDataEmbed(ign,
                        getUser().getId()));

                delayedResponseBuilder.setHighLevelComponents(new ActionRowBuilder().addComponents(
                        new ButtonBuilder().setStyle(ButtonStyle.LINK)
                                .setUrl(ConfigProperty.SKYCRYPT_API_URL + "stats/" + ign)
                                .setLabel("SkyCrypt")
                                .build()
                ).build());
            }
            catch (FailedToLoadEmbedException failedToLoadEmbedException) {
                delayedResponseBuilder.setEmbed(failedToLoadEmbedException.getEmbed()
                        .setColor(EmbedColor.NEGATIVE.getColor()));
            }
            catch (PlayerNotFoundException playerNotFoundException) {
                delayedResponseBuilder.setEmbed(ApplicationService.getInstance().getErrorEmbed(playerNotFoundException));
            }
            catch (CommandExecutionException commandExecutionException) {
                delayedResponseBuilder.setEmbed(ApplicationService.getInstance().getErrorEmbed(commandExecutionException));
                logger.error(null, commandExecutionException);
            }

            return delayedResponseBuilder.build();
        }));
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