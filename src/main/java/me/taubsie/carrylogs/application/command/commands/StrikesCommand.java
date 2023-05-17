package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.classes.PageableMessage;
import me.taubsie.carrylogs.application.classes.StrikeMessage;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.dungeonhub.common.StrikeData;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "strikes", description = "See your strikes.")
public class StrikesCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        User userToCheck;
        try {
            userToCheck = getUserOption("user");
        }
        catch(InvalidOptionException invalidOptionException) {
            userToCheck = getUser();
        }

        User finalUserToCheck = userToCheck;
        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater(true)
                .thenAccept(responseUpdater -> {
                    List<StrikeData> strikeData = ConnectionService.getInstance()
                            .loadValidStrikeData(getServer().getId(), finalUserToCheck.getId());

                    int maxPage = ConnectionService.getInstance().getMaxValidStrikePage(getServer().getId(), finalUserToCheck.getId());

                    EmbedBuilder embed = ApplicationService.getInstance().formatStrikes(strikeData, finalUserToCheck, 1);

                    Message message = responseUpdater
                            .addEmbed(embed)
                            .addComponents(PageableMessage.getComponents(true, maxPage == 1))
                            .update()
                            .join();

                    new StrikeMessage(1, message.getChannel().getId(), message.getId(), finalUserToCheck.getId());
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to get the strikes of")
                .setRequired(false)
                .build();

        return List.of(userOption);
    }
}