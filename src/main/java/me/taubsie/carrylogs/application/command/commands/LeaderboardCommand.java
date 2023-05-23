package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.messages.PageableMessage;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Map;

@CommandParameters(name = "leaderboard", description = "Shows you a certain leaderboard.", enabledInDms = true)
public class LeaderboardCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String type = getStringOption("leaderboard");

        String leaderboardTitle = LeaderboardService.getInstance().getLeaderboardTitle(type);

        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater()
                .thenAccept(responseUpdater -> {
                    Map<Long, Long> score = ConnectionService.getInstance().getLeaderboardData(type.toLowerCase(), 1);

                    int maxPage = ConnectionService.getInstance().getMaxLeaderboardPage(type);

                    EmbedBuilder embed = LeaderboardService.getInstance().getLeaderboardEmbed(leaderboardTitle, score,
                            1, maxPage);

                    Message message = responseUpdater
                            .addEmbed(embed)
                            .addComponents(PageableMessage.getComponents(true, maxPage == 1))
                            .update()
                            .join();

                    LeaderboardService.getInstance().registerPageListener(message, type);
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOptionBuilder typeOptionBuilder = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("leaderboard")
                .setDescription("Select which leaderboard you want to see.")
                .setRequired(true);

        LeaderboardService.getInstance().getAvailableTypes().forEach(s -> typeOptionBuilder.addChoice(s, s));

        return List.of(typeOptionBuilder.build());
    }
}