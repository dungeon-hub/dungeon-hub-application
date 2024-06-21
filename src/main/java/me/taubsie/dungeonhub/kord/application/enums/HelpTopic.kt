package me.taubsie.dungeonhub.kord.application.enums

import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import lombok.Getter
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.kord.application.exceptions.MustBeServerException
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel
import me.taubsie.dungeonhub.kord.application.misc.HelpDisplay
import me.taubsie.dungeonhub.kord.application.enums.HelpTopic.DescriptionSupplier

@Getter
enum class HelpTopic(
    override val readableName: String,
    val title: String,
    val description: DescriptionSupplier
) : ChoiceEnum {
    SCORE("score", "Carry Score",
        DescriptionSupplier { _: User, server: Guild? ->
            if (server == null) {
                return@DescriptionSupplier HelpDisplay.fromException(MustBeServerException())
            }

            val fields: MutableMap<CarryTypeModel, MutableMap<String, Int>> = HashMap()

            val carryDifficulties =
                DiscordServerConnection.getInstance()
                    .getAllCarryDifficulties(server.id.value.toLong())
                    .orElse(ArrayList())

            if (carryDifficulties.isEmpty()) {
                return@DescriptionSupplier HelpDisplay.fromDescription(
                    """
                            You gain score based on the carries that you do.
                            Different types of carries give you certain score.
                            No scores have been set up yet!
                            """.trimIndent()
                )
            }

            val description = """
                You gain score based on the carries that you do.
                Different types of carries give you certain score:
                """.trimIndent()

            val carryDifficultiesByCarryType: MutableMap<CarryTypeModel, MutableList<CarryDifficultyModel>> =
                HashMap()

            for (carryDifficulty in carryDifficulties) {
                if (carryDifficultiesByCarryType.containsKey(carryDifficulty.carryType)) {
                    carryDifficultiesByCarryType[carryDifficulty.carryType]!!.add(carryDifficulty)
                } else {
                    carryDifficultiesByCarryType[carryDifficulty.carryType] = mutableListOf(carryDifficulty)
                }
            }

            for ((key, value) in carryDifficultiesByCarryType) {
                if (value.isEmpty()) {
                    continue
                }

                for (carryDifficulty in value) {
                    val hasMultipleWithSameName = value.stream()
                        .filter { otherDifficulty: CarryDifficultyModel ->
                            otherDifficulty.displayName.equals(carryDifficulty.displayName, ignoreCase = true)
                        }
                        .count() >= 2

                    val sameScoreForEach = value.stream()
                        .allMatch { otherDifficulty: CarryDifficultyModel -> otherDifficulty.score == carryDifficulty.score }

                    if (sameScoreForEach) {
                        fields[key] = mutableMapOf("Any" to carryDifficulty.score)
                        break
                    }

                    if (hasMultipleWithSameName) {
                        val sameScoreForName = value.stream()
                            .filter { otherDifficulty: CarryDifficultyModel ->
                                otherDifficulty.displayName
                                    .equals(carryDifficulty.displayName, ignoreCase = true)
                            }
                            .allMatch { otherDifficulty: CarryDifficultyModel -> otherDifficulty.score == carryDifficulty.score }

                        if (sameScoreForName) {
                            if (fields.getOrDefault(key, mapOf())
                                    .entries.stream()
                                    .anyMatch { field: Map.Entry<String, Int> ->
                                        field.key.equals(
                                            carryDifficulty.displayName,
                                            ignoreCase = true
                                        ) && field.value == carryDifficulty.score
                                    }
                            ) {
                                continue
                            }

                            if (fields.containsKey(key)) {
                                fields[key]!![carryDifficulty.displayName] = carryDifficulty.score
                            } else {
                                fields[key] = HashMap(
                                    mutableMapOf(carryDifficulty.displayName to carryDifficulty.score)
                                )
                            }
                        } else {
                            if (fields.containsKey(key)) {
                                fields[key]!![carryDifficulty.carryTier.displayName +
                                        " | "
                                        + carryDifficulty.displayName] = carryDifficulty.score
                            } else {
                                fields[key] = HashMap(
                                    mutableMapOf(
                                        carryDifficulty.carryTier.displayName + " " + "| " + carryDifficulty.displayName to carryDifficulty.score
                                    )
                                )
                            }
                        }
                    } else {
                        if (fields.containsKey(key)) {
                            fields[key]!![carryDifficulty.displayName] = carryDifficulty.score
                        } else {
                            fields[key] = HashMap(
                                mutableMapOf(carryDifficulty.displayName to carryDifficulty.score)
                            )
                        }
                    }
                }
            }

            val embedFields: MutableMap<String, String> = HashMap()

            for ((key, value1) in fields) {
                val value = java.lang.String.join(
                    "\n", value1
                        .entries.stream()
                        .sorted(
                            java.util.Map.Entry.comparingByValue<String, Int>()
                                .thenComparing(java.util.Map.Entry.comparingByKey())
                        )
                        .map { entry: Map.Entry<String, Int> -> entry.key + " - " + entry.value }
                        .toList()
                )

                embedFields[key.displayName] = value
            }

            HelpDisplay(description, EmbedColor.DEFAULT, embedFields)
        }),
    VERIFICATION("verification", "How to verify",
        DescriptionSupplier { user: User, _: Guild? ->
            HelpDisplay.fromDescription(
                "To link your Minecraft account to your Discord account:\n" +
                        "## 1. On Hypixel\n" +
                        "- Join any lobby using `/lobby`\n" +
                        "- Right click with the \"My Profile\" item (second hotbar slot) in your hand.\n" +
                        "- Select \"Social Media\", to the right of your player head, then select \"Discord\".\n" +
                        "- Paste your Discord username (" + user.username + ") exactly like that into the chat.\n" +
                        "- If you get shown a book, select \"I understand\".\n" +
                        "## 2. On Discord\n" +
                        "- Use the `/link` command, this should complete the process.\n\n" +
                        "Please keep in mind that the API can take up to 15 minutes to update the changes made " +
                        "on Hypixel. If you have an issue with verification and only recently changed your " +
                        "settings, wait a few minutes and try again!\n" +
                        "\n" +
                        "You can find a video example [here]("
                        + ContentConnection.getInstance()
                    .getStaticUrl(KnownStaticResource.VERIFICATION_EXAMPLE.path).build().toUrl()
                        + ")."
            )
        });

    fun interface DescriptionSupplier {
        fun getDescription(user: User, server: Guild?): HelpDisplay
    }
}