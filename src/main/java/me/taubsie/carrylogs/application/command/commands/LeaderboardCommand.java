package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Map;

@CommandParameters(name = "leaderboard", description = "Shows you a certain leaderboard.", enabledInDms = true)
public class LeaderboardCommand extends Command {
    private static final List<String> choices = List.of("dungeons", "slayer", "kuudra", "alltime-dungeons", "alltime-slayer", "alltime-kuudra", "event-dungeons", "event-slayer");

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        //TODO rather write a method that dynamically builds the leaderboard
        //TODO make leaderboard have pages if run through command
        String type = getStringOption("leaderboard");

        String leaderboardTitle = switch(type.toLowerCase()) {
            case "dungeons" -> "Leaderboard | Dungeon-Carries";
            case "slayer" -> "Leaderboard | Slayer-Carries";
            case "kuudra" -> "Leaderboard | Kuudra-Carries";
            case "alltime-dungeons" -> "Leaderboard | Dungeon-Carries (all-time)";
            case "alltime-slayer" -> "Leaderboard | Slayer-Carries (all-time)";
            case "alltime-kuudra" -> "Leaderboard | Kuudra-Carries (all-time)";
            case "event-slayer" -> "Leaderboard | Slayer-Carries (event)";
            case "event-dungeons" -> "Leaderboard | Dungeon-Carries (event)";
            default -> "Leaderboard | Unknown [Please report]";
        };

        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater()
                .thenAccept(responseUpdater -> {
                    Map<Long, Long> score = ConnectionService.getInstance().getLeaderboard(type.toLowerCase());

                    int counter = 0;
                    EmbedBuilder embed = ApplicationService.getInstance()
                            .getEmbed()
                            .setTitle(leaderboardTitle)
                            .setColor(EmbedColor.DEFAULT.getColor());

                    if(score.isEmpty()) {
                        embed.setDescription("No score has been gained yet!\n" +
                                "To see how score works, use /score-help");
                    } else {
                        embed.setDescription("To see how score works, use /score-help");
                    }

                    for(Map.Entry<Long, Long> entry : score.entrySet()) {
                        User carrier = slashCommandCreateEvent.getApi().getUserById(entry.getKey()).join();
                        embed.addField(
                                "#" + ++counter + " Carrier",
                                carrier.getMentionTag() + " - " + entry.getValue() + " Score"
                        );
                    }

                    responseUpdater.addEmbed(embed).update();
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOptionBuilder typeOptionBuilder = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("leaderboard")
                .setDescription("Select which leaderboard you want to see.")
                .setRequired(true);

        choices.forEach(s -> typeOptionBuilder.addChoice(s, s));

        return List.of(typeOptionBuilder.build());
    }
}