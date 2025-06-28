package net.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.PurgeData
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.application.service.PurgingService
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.*
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.i18n.Translations.Command.Purge
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.discord_role.DiscordRoleModel
import net.dungeonhub.model.discord_user.DiscordUserModel
import net.dungeonhub.model.purge_type.PurgeTypeRoleModel
import net.dungeonhub.model.score.ScoreModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

/**
 * The /purge command allows you to purge inactive carriers (which means to remove their carrier roles).
 * It has the following subcommands:
 * - `show`: Shows which users would be affected by the purge.
 * - `add`: Adds the users to the current purge wave.
 * - `start`: Starts the current purge wave on the server.
 * - `progress`: Shows you the progress of the current purge wave.
 * - `clear`: Clears the current purge wave.
 */
@LoadExtension
class PurgeCommand : Extension() {
    private val logger: Logger =
        LoggerFactory.getLogger(PurgeCommand::class.java)

    override val name = "purge-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = Purge.name
            description = Purge.description
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::PurgeArguments) {
                name = "show".toKey()
                description = "Shows which users would be affected by the purge.".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")

                        val queue =
                            QueueConnection.authenticated().getCarryQueuesByQueueStep(QueueStep.Approving) ?: setOf()

                        val purgeType =
                            PurgeTypeConnection[carryType].authenticated().getByIdentifier(arguments.purgeType)
                                ?: throw InvalidOptionException("purge-type", "Purge Type couldn't be found.")

                        val rolesToRemove = purgeType.purgeTypeRoleModels.stream()
                            .map { obj: PurgeTypeRoleModel -> obj.discordRoleModel }
                            .toList()

                        val scores = (ScoreConnection[carryType].authenticated().scores ?: listOf())
                            .filter { scoreModel: ScoreModel -> scoreModel.scoreType == ScoreType.Default }

                        val safeCarriers = scores.stream()
                            .filter { scoreModel: ScoreModel -> scoreModel.scoreAmount != null }
                            .filter { scoreModel: ScoreModel -> scoreModel.scoreAmount!! >= arguments.threshold }
                            .map { obj: ScoreModel -> obj.carrier }
                            .map { obj: DiscordUserModel -> obj.id }
                            .toList()

                        val purgeCarriers = rolesToRemove.stream()
                            .map { obj: DiscordRoleModel -> obj.id }
                            .distinct()
                            .flatMap { roleId ->
                                runBlocking {
                                    guild!!.withStrategy(EntitySupplyStrategy.cachingRest)
                                        .members.filter { it.roleIds.contains(Snowflake(roleId)) }.toList().stream()
                                }
                            }
                            .distinct()
                            .filter { user -> !safeCarriers.contains(user.id.value.toLong()) }
                            .toList()

                        var amount = 0
                        val purgeDisplay: MutableList<String> = mutableListOf()

                        for (carrier in purgeCarriers) {
                            val score = scores.stream()
                                .filter { scoreModel: ScoreModel -> scoreModel.carrier.id == carrier.id.value.toLong() }
                                .map { obj: ScoreModel -> obj.scoreAmount }
                                .findFirst()
                                .orElse(0L)

                            purgeDisplay.add(carrier.mention + " - " + score + " score")

                            amount++
                        }

                        val purgedList = java.lang.String.join(System.lineSeparator(), purgeDisplay)

                        val description = (if (queue.any { queueModel -> queueModel.carryType.id == carryType.id }) {
                            "There are still unapproved logs waiting in the queue.\nPlease make sure to clear them before starting a purge.\n\n"
                        } else "") +
                                if (purgedList.length >= 4000) {
                                    (("The list of carriers purged would be too long.\n"
                                            + ((ContentConnection.authenticated()
                                        .uploadFile(purgedList.toByteArray(StandardCharsets.UTF_8))
                                        ?.let { s: String -> "https://cdn.dungeon-hub.net/$s" })
                                        ?: "The full list has been logged, contact administrators for more information.")))
                                } else {
                                    purgedList
                                }

                        val embed = ApplicationService.embed
                        if (queue.any { queueModel -> queueModel.carryType.id == carryType.id }) {
                            embed.color(EmbedColor.Negative)
                        } else {
                            embed.color(EmbedColor.Default)
                        }
                        embed.title = "The following $amount carriers would be purged."
                        embed.description = description

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::PurgeArguments) {
                name = "add".toKey()
                description = "Adds the users to the current purge wave.".toKey()

                action {
                    respond {
                        val carryType = CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                            .getByIdentifier(arguments.carryType)

                        if (carryType == null) {
                            throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")
                        }

                        val queue =
                            QueueConnection.authenticated().getCarryQueuesByQueueStep(QueueStep.Approving) ?: setOf()

                        if (queue.any { queueModel -> queueModel.carryType.id == carryType.id }) {
                            val embed = ApplicationService.embed
                            embed.color(EmbedColor.Negative)
                            embed.description =
                                "There are still unapproved logs waiting in the queue. Please make sure to clear them before starting a purge."
                            embeds = mutableListOf(embed)
                            return@respond
                        }

                        val purgeType =
                            PurgeTypeConnection[carryType].authenticated().getByIdentifier(arguments.purgeType)
                                ?: throw InvalidOptionException("purge-type", "Purge Type couldn't be found.")

                        val rolesToRemove = purgeType.purgeTypeRoleModels.stream()
                            .map { it.discordRoleModel }
                            .toList()

                        val scores = (ScoreConnection[carryType].authenticated().scores ?: listOf())
                            .filter { it.scoreType == ScoreType.Default }

                        val safeCarriers = scores.stream()
                            .filter { it.scoreAmount != null }
                            .filter { it.scoreAmount!! >= arguments.threshold }
                            .map { it.carrier }
                            .map { um -> um.id }
                            .toList()

                        val purgeCarriers = rolesToRemove.stream()
                            .map { it.id }
                            .distinct()
                            .flatMap { roleId ->
                                runBlocking {
                                    guild!!.withStrategy(EntitySupplyStrategy.cachingRest)
                                        .members.filter { it.roleIds.contains(Snowflake(roleId)) }.toList().stream()
                                }
                            }
                            .distinct()
                            .filter { !safeCarriers.contains(it.id.value.toLong()) }
                            .toList()

                        var amount = 0
                        val purgeDisplay: MutableList<String> = ArrayList()

                        for (carrier in purgeCarriers) {
                            val score = scores.stream()
                                .filter { scoreModel -> scoreModel.carrier.id == carrier.id.value.toLong() }
                                .map { obj: ScoreModel -> obj.scoreAmount }
                                .findFirst()
                                .orElse(0L) ?: 0

                            val purgeData = PurgeData(
                                carrier.id.value.toLong(),
                                rolesToRemove,
                                score,
                                purgeType,
                                arguments.threshold
                            )

                            PurgingService.addPurgeData(purgeData)

                            purgeDisplay.add(carrier.mention + " - " + score + " score")

                            amount++
                        }

                        val purgedList = java.lang.String.join("\n", purgeDisplay)

                        val description = if (purgedList.length >= 4000) {
                            ((("The list of carriers purged would be too long.\n"
                                    + (((ContentConnection.authenticated()
                                .uploadFile(purgedList.toByteArray(StandardCharsets.UTF_8))
                                ?.let { s: String -> "https://cdn.dungeon-hub.net/$s" }))
                                ?: "The full list has been logged, contact administrators for more information."))))
                        } else {
                            purgedList
                        }

                        logger.info("Purge data for type \"{}\":", purgeType.identifier)
                        logger.info(purgedList)

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Default.color
                        embed.title = "Added the roles of $amount carriers to removal-list."
                        embed.description = description

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand {
                name = "start".toKey()
                description = "Starts the current purge wave.".toKey()

                action {
                    respond {
                        PurgingService.enablePurge(guild!!.id.value.toLong())

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Negative.color
                        embed.title = "Purge started."

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand {
                name = "progress".toKey()
                description = "Shows you the progress of the current purge wave.".toKey()

                action {
                    respond {
                        val progress = PurgingService.getProgress(guild!!.id.value.toLong())
                        val userProgress = PurgingService.getUserProgress(guild!!.id.value.toLong())

                        if (progress <= 0) {
                            throw CommandExecutionException("There is no active purge.")
                        }

                        val embed = ApplicationService.embed
                        embed.title = "Current purge"
                        embed.color = EmbedColor.Default.color

                        if (PurgingService.isPurgeActive(guild!!.id.value.toLong())) {
                            embed.description = "$userProgress users are left. ($progress actions)"
                        } else {
                            embed.description = "$userProgress users will be purged. ($progress actions)"
                        }

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand {
                name = "clear".toKey()
                description = "Clears the current purge wave.".toKey()

                action {
                    respond {
                        if (PurgingService.isPurgeActive(guild!!.id.value.toLong())) {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Negative.color
                            embed.description = "The purge is already ongoing, it isn't possible to stop it."

                            embeds = mutableListOf(embed)
                            return@respond
                        }

                        PurgingService.clearServer(guild!!.id.value.toLong())

                        val embed = ApplicationService.embed
                        embed.title = "Purge cleared"
                        embed.color = EmbedColor.Default.color

                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    class PurgeArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val purgeType by string {
            name = "purge-type".toKey()
            description = "The identifier of the purge type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.purgeType
        }

        val threshold by long {
            name = "threshold".toKey()
            description = "The score-threshold.".toKey()
            minValue = 0
            maxValue = 100
        }
    }
}