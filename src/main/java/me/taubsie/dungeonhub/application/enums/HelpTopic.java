package me.taubsie.dungeonhub.application.enums;

import lombok.Getter;
import me.taubsie.dungeonhub.application.classes.HelpDisplay;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.application.exceptions.MustBeServerException;
import me.taubsie.dungeonhub.common.Nameable;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import okhttp3.HttpUrl;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public enum HelpTopic implements Nameable {
    SCORE("score", "Carry Score",
            (user, server) -> {
                if (server == null) {
                    return HelpDisplay.fromException(new MustBeServerException());
                }

                Map<CarryTypeModel, Map<String, Integer>> fields = new HashMap<>();

                List<CarryDifficultyModel> carryDifficulties = DiscordServerConnection.getInstance()
                        .getAllCarryDifficulties(server.getId())
                        .orElse(new ArrayList<>());

                if (carryDifficulties.isEmpty()) {
                    return HelpDisplay.fromDescription("""
                            You gain score based on the carries that you do.
                            Different types of carries give you certain score.
                            No scores have been set up yet!""");
                }

                String description = "You gain score based on the carries that you do.\n" +
                        "Different types of carries give you certain score:";

                Map<CarryTypeModel, List<CarryDifficultyModel>> carryDifficultiesByCarryType = new HashMap<>();

                for(CarryDifficultyModel carryDifficulty : carryDifficulties) {
                    if (carryDifficultiesByCarryType.containsKey(carryDifficulty.getCarryType())) {
                        carryDifficultiesByCarryType.get(carryDifficulty.getCarryType()).add(carryDifficulty);
                    } else {
                        carryDifficultiesByCarryType.put(carryDifficulty.getCarryType(),
                                new ArrayList<>(List.of(carryDifficulty)));
                    }
                }

                for(Map.Entry<CarryTypeModel, List<CarryDifficultyModel>> entry :
                        carryDifficultiesByCarryType.entrySet()) {
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
                                    fields.get(entry.getKey()).put(carryDifficulty.getCarryTier().getDisplayName() +
                                            " | "
                                            + carryDifficulty.getDisplayName(), carryDifficulty.getScore());
                                } else {
                                    fields.put(entry.getKey(),
                                            new HashMap<>(Map.of(carryDifficulty.getCarryTier().getDisplayName() + " " +
                                                            "| " + carryDifficulty.getDisplayName(),
                                                    carryDifficulty.getScore())));
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

                Map<String, String> embedFields = new HashMap<>();

                for(Map.Entry<CarryTypeModel, Map<String, Integer>> field : fields.entrySet()) {
                    String value = String.join("\n", field.getValue()
                            .entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue()
                                    .thenComparing(Map.Entry.comparingByKey()))
                            .map(entry -> entry.getKey() + " - " + entry.getValue())
                            .toList());

                    embedFields.put(field.getKey().getDisplayName(), value);
                }

                return new HelpDisplay(description, EmbedColor.DEFAULT, embedFields);
            }),
    VERIFICATION("verification", "How to verify",
            (user, server) -> HelpDisplay.fromDescription(
                    "To link your Minecraft account to your Discord account:\n" +
                            "## 1. On Hypixel\n" +
                            "- Join any lobby using `/lobby`\n" +
                            "- Right click with the \"My Profile\" item (second hotbar slot) in your hand.\n" +
                            "- Select \"Social Media\", to the right of your player head, then select \"Discord\".\n" +
                            "- Paste your Discord username (" + (user.getDiscriminator().equals("0") ?
                            user.getName() : user.getDiscriminatedName()) + ") exactly like that into the chat.\n" +
                            "- If you get shown a book, select \"I understand\".\n" +
                            "## 2. On Discord\n" +
                            "- Use the `/link` command, this should complete the process.\n\n" +
                            "Please keep in mind that the API can take up to 15 minutes to update the changes made " +
                            "on Hypixel. If you have an issue with verification and only recently changed your " +
                            "settings, wait a few minutes and try again!\n" +
                            "\n" +
                            "You can find a video example [here]("
                            //+ ContentConnection.getInstance().getStaticUrl(KnownStaticResource.VERIFICATION_EXAMPLE
                            // .getPath()).build().url()
                            + HttpUrl.get("https://static.dungeon-hub.net/verification-example.mp4")
                            + ")."
            ));

    final String name;
    final String title;
    final DescriptionSupplier description;

    HelpTopic(String name, String title, DescriptionSupplier description) {
        this.name = name;
        this.title = title;
        this.description = description;
    }

    @Override
    public String getName() {
        return name;
    }

    public interface DescriptionSupplier {
        HelpDisplay getDescription(User user, @Nullable Server server);
    }
}