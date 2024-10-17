package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import dev.kordex.core.commands.converters.AutoCompleteCallback
import me.taubsie.dungeonhub.application.connection.dungeon_hub.*

object AutoCompletionService {
    val carryType: AutoCompleteCallback = { event ->
        suggest(
            CarryTypeConnection.getInstance(event.getGuildId())
                .allCarryTypes
                .orElse(listOf())
                .stream()
                .filter { carryType ->
                    focusedOption.value.isEmpty()
                            || (carryType.identifier.contains(focusedOption.value, true)
                            || carryType.displayName.contains(focusedOption.value, true))
                }
                .map { carryType ->
                    Choice.StringChoice(
                        name = carryType.displayName,
                        value = carryType.identifier,
                        nameLocalizations = Optional()
                    )
                }
                .toList()
        )
    }

    val carryTier: AutoCompleteCallback = { event ->
        val allOptions = event.interaction.command.options

        val carryType: String? = allOptions.filter { entry ->
            entry.key.equals("carry-type", ignoreCase = true)
        }.values.firstOrNull()?.value as String?

        if (carryType != null) {
            suggest(
                CarryTypeConnection.getInstance(event.getGuildId())
                    .getByIdentifier(carryType)
                    .flatMap { carryTypeModel ->
                        CarryTierConnection.getInstance(
                            carryTypeModel
                        ).allCarryTiers
                    }
                    .orElse(listOf())
                    .stream()
                    .filter { carryTier ->
                        focusedOption.value.isEmpty()
                                || (carryTier.identifier.contains(focusedOption.value, true)
                                || carryTier.displayName.contains(focusedOption.value, true))
                    }
                    .map { carryTier ->
                        Choice.StringChoice(
                            name = carryTier.displayName,
                            value = carryTier.identifier,
                            nameLocalizations = Optional()
                        )
                    }
                    .toList()
            )
        }

        listOf<Choice>()
    }

    val carryDifficulty: AutoCompleteCallback = { event ->
        val allOptions = event.interaction.command.options

        val carryType: String? = allOptions.filter { entry ->
            entry.key.equals("carry-type", ignoreCase = true)
        }.values.firstOrNull()?.value as String?

        val carryTier: String? = allOptions.filter { entry ->
            entry.key.equals("carry-tier", ignoreCase = true)
        }.values.firstOrNull()?.value as String?

        if (carryTier != null) {
            val carryTierModel =
                CarryTypeConnection.getInstance(event.getGuildId())
                    .getByIdentifier(carryType)
                    .flatMap { carryTypeModel ->
                        CarryTierConnection.getInstance(
                            carryTypeModel
                        ).getByIdentifier(carryTier)
                    }.orElse(null)

            if (carryTierModel != null) {
                suggest(
                    CarryDifficultyConnection.getInstance(
                        carryTierModel
                    )
                        .allCarryDifficulties
                        .orElse(listOf())
                        .stream()
                        .filter { carryDifficulty ->
                            focusedOption.value.isEmpty()
                                    || (carryDifficulty.identifier.contains(focusedOption.value, true)
                                    || carryDifficulty.displayName.contains(focusedOption.value, true))
                        }
                        .map { carryDifficulty ->
                            Choice.StringChoice(
                                name = carryDifficulty.displayName,
                                value = carryDifficulty.identifier,
                                nameLocalizations = Optional()
                            )
                        }
                        .toList()
                )
            }
        } else {
            val categoryId = event.interaction.channel.asChannelOfOrNull<CategorizableChannel>()?.categoryId

            val carryTierByCategory = categoryId?.let { category ->
                DiscordServerConnection.getInstance()
                    .getCarryTierFromCategory(event.getGuildId(), category.value.toLong())
            }?.orElse(null)

            if (carryTierByCategory != null) {
                suggest(
                    CarryDifficultyConnection.getInstance(
                        carryTierByCategory
                    )
                        .allCarryDifficulties
                        .orElse(listOf())
                        .stream()
                        .filter { carryDifficulty ->
                            focusedOption.value.isEmpty()
                                    || (carryDifficulty.identifier.contains(focusedOption.value, true)
                                    || carryDifficulty.displayName.contains(focusedOption.value, true))
                        }
                        .map { carryDifficulty ->
                            Choice.StringChoice(
                                name = carryDifficulty.displayName,
                                value = carryDifficulty.identifier,
                                nameLocalizations = Optional()
                            )
                        }
                        .toList()
                )
            }
        }

        listOf<Choice>()
    }

    val purgeType: AutoCompleteCallback = { event ->
        val allOptions = event.interaction.command.options

        val carryType: String? = allOptions.filter { entry ->
            entry.key.equals("carry-type", ignoreCase = true)
        }.values.firstOrNull()?.value as String?

        if (carryType != null) {
            suggest(
                CarryTypeConnection.getInstance(event.getGuildId())
                    .getByIdentifier(carryType)
                    .flatMap { carryTypeModel ->
                        PurgeTypeConnection.getInstance(
                            carryTypeModel
                        ).allPurgeTypes
                    }
                    .orElse(listOf())
                    .stream()
                    .filter { purgeType ->
                        focusedOption.value.isEmpty()
                                || (purgeType.identifier.contains(focusedOption.value, true)
                                || purgeType.displayName.contains(focusedOption.value, true))
                    }
                    .map { purgeType ->
                        Choice.StringChoice(
                            name = purgeType.displayName,
                            value = purgeType.identifier,
                            nameLocalizations = Optional()
                        )
                    }
                    .toList()
            )
        }

        listOf<Choice>()
    }
}

private fun AutoCompleteInteractionCreateEvent.getGuildId(): Long {
    return interaction.data.guildId.value?.value!!.toLong()
}
