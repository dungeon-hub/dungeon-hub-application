package net.dungeonhub.application.service

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import dev.kordex.core.commands.converters.AutoCompleteCallback
import net.dungeonhub.application.enums.KnownStaticResource
import net.dungeonhub.connection.*

object AutoCompletionService {
    val carryType: AutoCompleteCallback = { event ->
        suggest(
            (CarryTypeConnection[event.getGuildId()].authenticated()
                .allCarryTypes
                ?: listOf())
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
        )
    }

    val carryTier: AutoCompleteCallback = { event ->
        val allOptions = event.interaction.command.options

        val carryType: String? = allOptions.filter { entry ->
            entry.key.equals("carry-type", ignoreCase = true)
        }.values.firstOrNull()?.value as String?

        if (carryType != null) {
            suggest(
                (CarryTypeConnection[event.getGuildId()].authenticated()
                    .getByIdentifier(carryType)
                    ?.let { carryTypeModel ->
                        CarryTierConnection[carryTypeModel].authenticated().allCarryTiers
                    } ?: listOf())
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
                CarryTypeConnection[event.getGuildId()].authenticated()
                    .getByIdentifier(carryType)
                    ?.let { carryTypeModel ->
                        CarryTierConnection[carryTypeModel].authenticated().getByIdentifier(carryTier)
                    }

            if (carryTierModel != null) {
                suggest(
                    (CarryDifficultyConnection[carryTierModel].authenticated()
                        .allCarryDifficulties ?: listOf())
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
                )
            }
        } else {
            val categoryId = event.interaction.channel.asChannelOfOrNull<CategorizableChannel>()?.categoryId

            val carryTierByCategory = categoryId?.let { category ->
                DiscordServerConnection.authenticated().getCarryTierFromCategory(event.getGuildId(), category.value.toLong())
            }

            if (carryTierByCategory != null) {
                suggest(
                    (CarryDifficultyConnection[carryTierByCategory].authenticated()
                        .allCarryDifficulties ?: listOf())
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
                (CarryTypeConnection[event.getGuildId()].authenticated()
                    .getByIdentifier(carryType)
                    ?.let { carryTypeModel ->
                        PurgeTypeConnection[carryTypeModel].authenticated().allPurgeTypes
                    } ?: listOf())
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
            )
        }

        listOf<Choice>()
    }

    val knownStaticResource: AutoCompleteCallback = { _ ->
        suggest(
            KnownStaticResource.entries.filter { staticResource ->
                focusedOption.value.isEmpty()
                        || (staticResource.name.contains(focusedOption.value, true)
                        || staticResource.path.contains(focusedOption.value, true)
                        || staticResource.loadDisplayName().contains(focusedOption.value, true))
            }.map { staticResource ->
                Choice.StringChoice(
                    name = staticResource.loadDisplayName(),
                    value = staticResource.name,
                    nameLocalizations = Optional()
                )
            }.take(25)
        )
    }
}

private fun AutoCompleteInteractionCreateEvent.getGuildId(): Long {
    return interaction.data.guildId.value?.value!!.toLong()
}
