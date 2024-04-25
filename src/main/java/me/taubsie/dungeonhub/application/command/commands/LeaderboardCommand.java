package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.messages.PageableMessage;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "leaderboard", description = "Shows you a certain leaderboard.")
public class LeaderboardCommand extends Command {
    public static SlashCommandOption getScoreTypeOption() {
        SlashCommandOptionBuilder scoreTypeOptionBuilder = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("score-type")
                .setDescription("Select which type of score you want.")
                .setRequired(false);

        Arrays.stream(ScoreType.values()).forEach(leaderboardType -> scoreTypeOptionBuilder.addChoice(leaderboardType.getDisplayName(), leaderboardType.getName()));

        return scoreTypeOptionBuilder.build();
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Optional<CarryTypeModel> carryType = getOptionalStringOption(slashCommandCreateEvent.getSlashCommandInteraction(), "carry-type")
                .flatMap(s -> CarryTypeConnection.getInstance(getServer().getId()).getByIdentifier(s));

        ScoreType scoreType = getEnumOption("score-type", ScoreType.class, ScoreType.DEFAULT);

        String leaderboardTitle = LeaderboardService.getInstance().getLeaderboardTitle(carryType.orElse(null), scoreType);

        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater()
                .thenAccept(responseUpdater -> {
                    Optional<LeaderboardModel> leaderboardModel = carryType.map(carryTypeModel -> ScoreConnection.getInstance(carryTypeModel).loadLeaderboard(scoreType, 0, getUser().getId()))
                            .orElseGet(() -> DiscordServerConnection.getInstance().loadTotalLeaderboard(getServer().getId(), scoreType, 0, getUser().getId()));

                    EmbedBuilder embed = leaderboardModel.map(model -> LeaderboardService.getInstance()
                                    .getLeaderboardEmbed(leaderboardTitle, model))
                            .orElseGet(() -> LeaderboardService.getInstance()
                                    .getEmptyLeaderboardEmbed(leaderboardTitle));

                    Message message = responseUpdater
                            .addEmbed(embed)
                            .addComponents(PageableMessage.getComponents(true,
                                    leaderboardModel.map(LeaderboardModel::getTotalPages).orElse(0) <= 1))
                            .update()
                            .join();

                    LeaderboardService.getInstance().registerPageListener(message, carryType.orElse(null), scoreType, getUser().getId());
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        return List.of(CarryTypeCommand.getCarryTypeOption(false), getScoreTypeOption());
    }
}