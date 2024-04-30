package me.taubsie.dungeonhub.kord.application.service

import com.kotlindiscord.kord.extensions.commands.converters.AutoCompleteCallback
import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.PurgeTypeConnection

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
