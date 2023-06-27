package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.MustBeServerException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryDifficulty;
import me.taubsie.dungeonhub.common.CarryType;
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

            Map<CarryType, Map<String, Integer>> fields = new HashMap<>();

            List<CarryDifficulty> carryDifficulties = DungeonHubConnection.getInstance()
                    .loadCarryDifficulties(server.get());

            if(carryDifficulties.isEmpty()) {
                return embed.setColor(EmbedColor.NEGATIVE.getColor())
                        .setDescription("""
                                You gain score based on the carries that you do.
                                Different types of carries give you certain score.
                                No scores have been set up yet!""");
            }

            embed.setDescription("You gain score based on the carries that you do.\n" +
                            "Different types of carries give you certain score:")
                    .setColor(EmbedColor.INFORMATION.getColor());

            Map<CarryType, List<CarryDifficulty>> carryDifficultiesByCarryType = new HashMap<>();

            for (CarryDifficulty carryDifficulty : carryDifficulties) {
                if (carryDifficultiesByCarryType.containsKey(carryDifficulty.getCarryType())) {
                    carryDifficultiesByCarryType.get(carryDifficulty.getCarryType()).add(carryDifficulty);
                } else {
                    carryDifficultiesByCarryType.put(carryDifficulty.getCarryType(), new ArrayList<>(List.of(carryDifficulty)));
                }
            }

            for (Map.Entry<CarryType, List<CarryDifficulty>> entry : carryDifficultiesByCarryType.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                for (CarryDifficulty carryDifficulty : entry.getValue()) {
                    boolean hasMultipleWithSameName = entry.getValue().stream()
                            .filter(otherDifficulty -> otherDifficulty.getDisplayName().equalsIgnoreCase(carryDifficulty.getDisplayName()))
                            .count() >= 2;

                    if (hasMultipleWithSameName) {
                        boolean sameScoreForEach = entry.getValue().stream()
                                .allMatch(otherDifficulty -> otherDifficulty.getScore() == carryDifficulty.getScore());

                        boolean sameScoreForName = entry.getValue().stream()
                                .filter(otherDifficulty -> otherDifficulty.getDisplayName().equalsIgnoreCase(carryDifficulty.getDisplayName()))
                                .allMatch(otherDifficulty -> otherDifficulty.getScore() == carryDifficulty.getScore());

                        if (sameScoreForEach) {
                            fields.put(entry.getKey(), Map.of("Any", carryDifficulty.getScore()));
                            break;
                        } else if (sameScoreForName) {
                            if (fields.getOrDefault(entry.getKey(), Map.of())
                                    .entrySet().stream().anyMatch(field -> field.getKey().equalsIgnoreCase(carryDifficulty.getDisplayName()) && field.getValue() == carryDifficulty.getScore())) {
                                continue;
                            }

                            if (fields.containsKey(entry.getKey())) {
                                fields.get(entry.getKey()).put(carryDifficulty.getDisplayName(), carryDifficulty.getScore());
                            } else {
                                fields.put(entry.getKey(), new HashMap<>(Map.of(carryDifficulty.getDisplayName(), carryDifficulty.getScore())));
                            }
                        } else {
                            if (fields.containsKey(entry.getKey())) {
                                fields.get(entry.getKey()).put(carryDifficulty.getCarryTier().getDisplayName() + " | " + carryDifficulty.getDisplayName(), carryDifficulty.getScore());
                            } else {
                                fields.put(entry.getKey(), new HashMap<>(Map.of(carryDifficulty.getCarryTier().getDisplayName() + " | " + carryDifficulty.getDisplayName(), carryDifficulty.getScore())));
                            }
                        }
                    } else {
                        if (fields.containsKey(entry.getKey())) {
                            fields.get(entry.getKey()).put(carryDifficulty.getDisplayName(), carryDifficulty.getScore());
                        } else {
                            fields.put(entry.getKey(), new HashMap<>(Map.of(carryDifficulty.getDisplayName(), carryDifficulty.getScore())));
                        }
                    }
                }
            }

            for (Map.Entry<CarryType, Map<String, Integer>> field : fields.entrySet()) {
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