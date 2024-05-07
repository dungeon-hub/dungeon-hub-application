package me.taubsie.dungeonhub.kord.application.service

import com.kotlindiscord.kord.extensions.commands.converters.AutoCompleteCallback
import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import me.taubsie.dungeonhub.application.connection.dungeon_hub.*

object AutoCompletion {
    val carryType: AutoCompleteCallback = { event ->
        suggest(
            CarryTypeConnection.getInstance(event.getGuildId())
                .allCarryTypes
                .orElse(listOf())
                .stream()
                .filter { carryType ->
                    focusedOption.value.isEmpty()
                            || (carryType.identifier.startsWith(focusedOption.value, true)
                            || carryType.displayName.startsWith(focusedOption.value, true))
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
            CarryTypeConnection.getInstance(event.getGuildId())
                .getByIdentifier(carryType)
                .flatMap { carryTypeModel ->
                    CarryTierConnection.getInstance(carryTypeModel).allCarryTiers
                }
                .orElse(listOf())
                .stream()
                .filter { carryTier ->
                    focusedOption.value.isEmpty()
                            || (carryTier.identifier.startsWith(focusedOption.value, true)
                            || carryTier.displayName.startsWith(focusedOption.value, true))
                }
                .map { carryTier ->
                    Choice.StringChoice(
                        name = carryTier.displayName,
                        value = carryTier.identifier,
                        nameLocalizations = Optional()
                    )
                }
                .toList()
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
            val carryTierModel = CarryTypeConnection.getInstance(event.getGuildId()).getByIdentifier(carryType)
                .flatMap { carryTypeModel ->
                    CarryTierConnection.getInstance(carryTypeModel).getByIdentifier(carryTier)
                }.orElse(null)

            if (carryTierModel != null) {
                CarryDifficultyConnection.getInstance(carryTierModel)
                    .allCarryDifficulties
                    .orElse(listOf())
                    .stream()
                    .filter { carryDifficulty ->
                        focusedOption.value.isEmpty()
                                || (carryDifficulty.identifier.startsWith(focusedOption.value, true)
                                || carryDifficulty.displayName.startsWith(focusedOption.value, true))
                    }
                    .map { carryDifficulty ->
                        Choice.StringChoice(
                            name = carryDifficulty.displayName,
                            value = carryDifficulty.identifier,
                            nameLocalizations = Optional()
                        )
                    }
                    .toList()
            }
        } else {
            val categoryId = event.interaction.channel.asChannelOfOrNull<CategorizableChannel>()?.categoryId

            val carryTierByCategory = categoryId?.let { category ->
                DiscordServerConnection.getInstance()
                    .getCarryTierFromCategory(event.getGuildId(), category.value.toLong())
            }?.orElse(null)

            if (carryTierByCategory != null) {
                CarryDifficultyConnection.getInstance(carryTierByCategory)
                    .allCarryDifficulties
                    .orElse(listOf())
                    .stream()
                    .filter { carryDifficulty ->
                        focusedOption.value.isEmpty()
                                || (carryDifficulty.identifier.startsWith(focusedOption.value, true)
                                || carryDifficulty.displayName.startsWith(focusedOption.value, true))
                    }
                    .map { carryDifficulty ->
                        Choice.StringChoice(
                            name = carryDifficulty.displayName,
                            value = carryDifficulty.identifier,
                            nameLocalizations = Optional()
                        )
                    }
                    .toList()
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
            CarryTypeConnection.getInstance(event.getGuildId())
                .getByIdentifier(carryType)
                .flatMap { carryTypeModel ->
                    PurgeTypeConnection.getInstance(carryTypeModel).allPurgeTypes
                }
                .orElse(listOf())
                .stream()
                .filter { purgeType ->
                    focusedOption.value.isEmpty()
                            || (purgeType.identifier.startsWith(focusedOption.value, true)
                            || purgeType.displayName.startsWith(focusedOption.value, true))
                }
                .map { purgeType ->
                    Choice.StringChoice(
                        name = purgeType.displayName,
                        value = purgeType.identifier,
                        nameLocalizations = Optional()
                    )
                }
                .toList()
        }

        listOf<Choice>()
    }
}

private fun AutoCompleteInteractionCreateEvent.getGuildId(): Long {
    return interaction.data.guildId.value?.value!!.toLong()
}
