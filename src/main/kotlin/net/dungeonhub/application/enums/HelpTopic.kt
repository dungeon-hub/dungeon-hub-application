package net.dungeonhub.application.enums

import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key
import lombok.Getter
import net.dungeonhub.application.exceptions.MustBeServerException
import net.dungeonhub.application.misc.HelpDisplay
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel
import net.dungeonhub.model.carry_type.CarryTypeModel

@Getter
enum class HelpTopic(
    override val readableName: Key,
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
                DiscordServerConnection.getAllCarryDifficulties(server.id.value.toLong()) ?: ArrayList()

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

            HelpDisplay(description, EmbedColor.Default, embedFields)
        }),
    VERIFICATION("verification", "How to verify",
        DescriptionSupplier { user: User, _: Guild? ->
            HelpDisplay.fromDescription(
                "To link your Minecraft account to your Discord account:\n" +
                        "## 1. On Hypixel (`mc.hypixel.net`)\n" +
                        "- Join any lobby using `/lobby`\n" +
                        "- Right click with the \"My Profile\" item (second hotbar slot) in your hand.\n" +
                        "- Select \"Social Media\", to the right of your player head, then select \"Discord\".\n" +
                        "- Paste your Discord username (" + user.username + ") exactly like that into the chat.\n" +
                        "- If you get shown a book, select \"I understand\".\n" +
                        "## 2. On Discord\n" +
                        "- Use the `/link` command, this should complete the process.\n\n" +
                        "Please keep in mind that the API can take up to 15 minutes to update the changes made " +
                        "on Hypixel. If you have an issue with verification and only recently changed your " +
                        "settings, wait a few minutes and try again!\n\n" +
                        "> If you think you're linked to the wrong Minecraft account, use the `/unlink` command.\n\n" +
                        "You can find a video example [here]("
                        + ContentConnection.getStaticUrl(KnownStaticResource.VerificationExample.path).build().toUrl()
                        + ")."
            )
        });

    constructor(readableName: String, title: String, description: DescriptionSupplier) : this(readableName.toKey(), title, description)

    fun interface DescriptionSupplier {
        fun getDescription(user: User, server: Guild?): HelpDisplay
    }
}
