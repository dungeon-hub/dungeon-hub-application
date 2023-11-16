package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ServerConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.MustBeServerException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "score-help",
        description = "Show an explanation about how score works.")
public class ScoreHelpCommand extends Command {
    @Override
    public void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Optional<Server> server = slashCommandCreateEvent.getSlashCommandInteraction().getServer();

        if (server.isEmpty()) {
            throw new MustBeServerException();
        }

        respondLaterEphemeral(new CompletableFuture<EmbedBuilder>().completeAsync(() -> {
            EmbedBuilder embed = ApplicationService.getInstance()
                    .getEmbed()
                    .setTitle("Carry Score");

            Map<CarryTypeModel, Map<String, Integer>> fields = new HashMap<>();

            List<CarryDifficultyModel> carryDifficulties = ServerConnection.getInstance()
                    .getAllCarryDifficulties(server.get().getId())
                    .orElse(new ArrayList<>());

            if (carryDifficulties.isEmpty()) {
                return embed.setColor(EmbedColor.NEGATIVE.getColor())
                        .setDescription("""
                                You gain score based on the carries that you do.
                                Different types of carries give you certain score.
                                No scores have been set up yet!""");
            }

            embed.setDescription("You gain score based on the carries that you do.\n" +
                            "Different types of carries give you certain score:")
                    .setColor(EmbedColor.INFORMATION.getColor());

            Map<CarryTypeModel, List<CarryDifficultyModel>> carryDifficultiesByCarryType = new HashMap<>();

            for(CarryDifficultyModel carryDifficulty : carryDifficulties) {
                if (carryDifficultiesByCarryType.containsKey(carryDifficulty.getCarryType())) {
                    carryDifficultiesByCarryType.get(carryDifficulty.getCarryType()).add(carryDifficulty);
                } else {
                    carryDifficultiesByCarryType.put(carryDifficulty.getCarryType(),
                            new ArrayList<>(List.of(carryDifficulty)));
                }
            }

            for(Map.Entry<CarryTypeModel, List<CarryDifficultyModel>> entry : carryDifficultiesByCarryType.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                for(CarryDifficultyModel carryDifficulty : entry.getValue()) {
                    boolean hasMultipleWithSameName = entry.getValue().stream()
                            .filter(otherDifficulty -> otherDifficulty.getDisplayName().equalsIgnoreCase(carryDifficulty.getDisplayName()))
                            .count() >= 2;

                    boolean sameScoreForEach = entry.getValue().stream()
                            .allMatch(otherDifficulty -> otherDifficulty.getScore() == carryDifficulty.getScore());

                    if (sameScoreForEach) {
                        fields.put(entry.getKey(), Map.of("Any", carryDifficulty.getScore()));
                        break;
                    }

                    if (hasMultipleWithSameName) {
                        boolean sameScoreForName = entry.getValue().stream()
                                .filter(otherDifficulty -> otherDifficulty.getDisplayName().equalsIgnoreCase(carryDifficulty.getDisplayName()))
                                .allMatch(otherDifficulty -> otherDifficulty.getScore() == carryDifficulty.getScore());

                        if (sameScoreForName) {
                            if (fields.getOrDefault(entry.getKey(), Map.of())
                                    .entrySet().stream().anyMatch(field -> field.getKey().equalsIgnoreCase(carryDifficulty.getDisplayName()) && field.getValue() == carryDifficulty.getScore())) {
                                continue;
                            }

                            if (fields.containsKey(entry.getKey())) {
                                fields.get(entry.getKey()).put(carryDifficulty.getDisplayName(),
                                        carryDifficulty.getScore());
                            } else {
                                fields.put(entry.getKey(), new HashMap<>(Map.of(carryDifficulty.getDisplayName(),
                                        carryDifficulty.getScore())));
                            }
                        } else {
                            if (fields.containsKey(entry.getKey())) {
                                fields.get(entry.getKey()).put(carryDifficulty.getCarryTier().getDisplayName() + " | "
                                        + carryDifficulty.getDisplayName(), carryDifficulty.getScore());
                            } else {
                                fields.put(entry.getKey(),
                                        new HashMap<>(Map.of(carryDifficulty.getCarryTier().getDisplayName() + " | " + carryDifficulty.getDisplayName(), carryDifficulty.getScore())));
                            }
                        }
                    } else {
                        if (fields.containsKey(entry.getKey())) {
                            fields.get(entry.getKey()).put(carryDifficulty.getDisplayName(),
                                    carryDifficulty.getScore());
                        } else {
                            fields.put(entry.getKey(), new HashMap<>(Map.of(carryDifficulty.getDisplayName(),
                                    carryDifficulty.getScore())));
                        }
                    }
                }
            }

            for(Map.Entry<CarryTypeModel, Map<String, Integer>> field : fields.entrySet()) {
                String value = String.join("\n", field.getValue()
                        .entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .map(entry -> entry.getKey() + " - " + entry.getValue())
                        .toList());

                embed.addField(field.getKey().getDisplayName(), value);
            }

            return embed;
        }));
    }
}