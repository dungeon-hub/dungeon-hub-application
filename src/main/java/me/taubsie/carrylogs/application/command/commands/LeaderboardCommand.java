package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.messages.PageableMessage;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.CarryType;
import me.taubsie.dungeonhub.common.LeaderboardType;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CommandParameters(name = "leaderboard", description = "Shows you a certain leaderboard.", enabledInDms = true)
public class LeaderboardCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Optional<CarryType> carryType = DungeonHubConnection.getInstance().loadCarryType(getServer().getId(), getStringOption("carry-type"));

        if(carryType.isEmpty()) {
            throw new InvalidOptionException("carry-type");
        }

        LeaderboardType leaderboardType = getEnumOption("leaderboard-type", LeaderboardType.class, LeaderboardType.DEFAULT);

        String leaderboardTitle = LeaderboardService.getInstance().getLeaderboardTitle(carryType.get(), leaderboardType);

        //TODO use respondLater() and CompletableFuture
        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater()
                .thenAccept(responseUpdater -> {
                    Map<Long, Long> score = DungeonHubConnection.getInstance().getLeaderboardData(carryType.get(), leaderboardType, 1);

                    int maxPage = DungeonHubConnection.getInstance().getMaxLeaderboardPage(carryType.get(), leaderboardType);

                    EmbedBuilder embed = LeaderboardService.getInstance().getLeaderboardEmbed(leaderboardTitle, score,
                            1, maxPage);

                    Message message = responseUpdater
                            .addEmbed(embed)
                            .addComponents(PageableMessage.getComponents(true, maxPage == 1))
                            .update()
                            .join();

                    LeaderboardService.getInstance().registerPageListener(message, carryType.get(), leaderboardType);
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption carryTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-type")
                .setDescription("Select which leaderboard you want to see.")
                .setAutocompletable(true)
                .setRequired(true)
                .build();

        SlashCommandOptionBuilder leaderboardTypeOptionBuilder = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("leaderboard-type")
                .setDescription("Select which type of leaderboard you want.")
                .setRequired(false);

        Arrays.stream(LeaderboardType.values()).forEach(leaderboardType -> leaderboardTypeOptionBuilder.addChoice(leaderboardType.getName(), leaderboardType.getName()));

        return List.of(carryTypeOption, leaderboardTypeOptionBuilder.build());
    }
}