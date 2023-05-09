package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandParameters(name = "score", enabledInDms = true, description = "Use this to count your or another user's " +
        "carries.")
public class ScoreCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElse(null);

        User userToCheck;
        try {
            userToCheck = getUserOption("user");
        }
        catch(InvalidOptionException invalidOptionException) {
            userToCheck = getUser();
        }

        Map<String, Long> scoreCount = ConnectionService.getInstance().countScore(userToCheck.getId());

        slashCommandCreateEvent
                .getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle((userToCheck.getId() != getUser().getId() && server != null)
                                ? userToCheck.getDisplayName(getServer()) + "'s score:"
                                : "Your score:")
                        .setColor(new Color(165, 23, 112 /*TODO color*/))
                        .addInlineField("Dungeon-Score:", String.valueOf(scoreCount.get("dungeon")))
                        .addInlineField("Slayer-Score:", String.valueOf(scoreCount.get("slayer")))
                        .addInlineField("Kuudra-Score:", String.valueOf(scoreCount.get("kuudra")))
                        .addInlineField("Alltime-Dungeon-Score:", String.valueOf(scoreCount.get("alltime-dungeon")))
                        .addInlineField("Alltime-Slayer-Score:", String.valueOf(scoreCount.get("alltime-slayer")))
                        .addInlineField("Alltime-Kuudra-Score:", String.valueOf(scoreCount.get("alltime-kuudra")))
                        .addInlineField("Event-Dungeon-Score:", String.valueOf(scoreCount.get("event-dungeon")))
                        .addInlineField("Event-Slayer-Score:", String.valueOf(scoreCount.get("event-slayer"))))
                .respond();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to check the carries for.")
                .setRequired(false)
                .build();

        return Collections.singletonList(userOption);
    }
}