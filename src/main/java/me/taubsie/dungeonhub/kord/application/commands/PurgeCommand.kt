package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.kord.application.misc.PurgeData
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.PurgeTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.PurgeTypeRoleModel
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel
import me.taubsie.dungeonhub.common.model.score.ScoreModel
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import me.taubsie.dungeonhub.kord.application.service.AutoCompletion
import me.taubsie.dungeonhub.kord.application.service.PurgingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

@LoadExtension
class PurgeCommand : Extension() {
    private val logger: Logger = LoggerFactory.getLogger(PurgeCommand::class.java)

    override val name = "purge-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "purge"
            description = "Allows you to purge inactive carriers."
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::PurgeArguments) {
                name = "show"
                description = "Shows which users would be affected by the purge."

                action {
                    respond {
                        val carryType = CarryTypeConnection.getInstance(guild!!.id.value.toLong())
                            .getByIdentifier(arguments.carryType)

                        if (carryType.isEmpty) {
                            throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")
                        }

                        val purgeType = PurgeTypeConnection.getInstance(carryType.get())
                            .getByIdentifier(arguments.purgeType)
                            .orElseThrow {
                                InvalidOptionException(
                                    "purge-type",
                                    "Purge Type couldn't be found."
                                )
                            }

                        val rolesToRemove = purgeType.purgeTypeRoleModels.stream()
                            .map { obj: PurgeTypeRoleModel -> obj.discordRoleModel }
                            .toList()

                        val scores = ScoreConnection.getInstance(carryType.get())
                            .scores
                            .orElse(listOf()).stream()
                            .filter { scoreModel: ScoreModel -> scoreModel.scoreType == ScoreType.DEFAULT }
                            .toList()

                        val safeCarriers = scores.stream()
                            .filter { scoreModel: ScoreModel -> scoreModel.scoreAmount != null }
                            .filter { scoreModel: ScoreModel -> scoreModel.scoreAmount >= arguments.threshold }
                            .map { obj: ScoreModel -> obj.carrier }
                            .map { obj: DiscordUserModel -> obj.id }
                            .toList()

                        val purgeCarriers = rolesToRemove.stream()
                            .map { obj: DiscordRoleModel -> obj.id }
                            .distinct()
                            .map { roleId ->
                                runBlocking {
                                    guild!!.getRoleOrNull(Snowflake(roleId))
                                }
                            }
                            .filter { it != null }
                            .flatMap { role ->
                                runBlocking {
                                    guild!!.members.filter { it.roleIds.contains(role!!.id) }.toList().stream()
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

                        val description = if (purgedList.length >= 4000) {
                            ("The list of carriers purged would be too long.\n"
                                    + ContentConnection.getInstance()
                                .uploadFile(purgedList.toByteArray(StandardCharsets.UTF_8))
                                .map { s: String -> "https://cdn.dungeon-hub.net/$s" }
                                .orElse("The full list has been logged, contact administrators for more information."))
                        } else {
                            purgedList
                        }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.DEFAULT.color
                        embed.title = "The following $amount carriers would be purged."
                        embed.description = description

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::PurgeArguments) {
                name = "add"
                description = "Adds the users to the current purge wave."

                action {
                    respond {
                        val carryType = CarryTypeConnection.getInstance(guild!!.id.value.toLong())
                            .getByIdentifier(arguments.carryType)

                        if (carryType.isEmpty) {
                            throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")
                        }

                        val purgeType = PurgeTypeConnection.getInstance(carryType.get())
                            .getByIdentifier(arguments.purgeType)
                            .orElseThrow { InvalidOptionException("purge-type", "Purge Type couldn't be found.") }

                        val rolesToRemove = purgeType.purgeTypeRoleModels.stream()
                            .map { it.discordRoleModel }
                            .toList()

                        val scores = ScoreConnection.getInstance(carryType.get())
                            .scores
                            .orElse(listOf()).stream()
                            .filter { it.scoreType == ScoreType.DEFAULT }
                            .toList()

                        val safeCarriers = scores.stream()
                            .filter { it.scoreAmount != null }
                            .filter { it.scoreAmount >= arguments.threshold }
                            .map { it.carrier }
                            .map { um -> um.id }
                            .toList()

                        val purgeCarriers = rolesToRemove.stream()
                            .map { um -> um.id }
                            .distinct()
                            .flatMap { roleId ->
                                runBlocking {
                                    guild!!.members.filter { it.roleIds.contains(Snowflake(roleId)) }.toList().stream()
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
                                .orElse(0L)

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
                            ("The list of carriers purged would be too long.\n"
                                    + ContentConnection.getInstance()
                                .uploadFile(purgedList.toByteArray(StandardCharsets.UTF_8))
                                .map { s: String -> "https://cdn.dungeon-hub.net/$s" }
                                .orElse("The full list has been logged, contact administrators for more information."))
                        } else {
                            purgedList
                        }

                        logger.info("Purge data for type \"{}\":", purgeType.identifier)
                        logger.info(purgedList)

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.DEFAULT.color
                        embed.title = "Added the roles of $amount carriers to removal-list."
                        embed.description = description

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand {
                name = "start"
                description = "Starts the current purge wave."

                action {
                    respond {
                        PurgingService.enablePurge(guild!!.id.value.toLong())

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.NEGATIVE.color
                        embed.title = "Purge started."

                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand {
                name = "progress"
                description = "Shows you the progress of the current purge wave."

                action {
                    respond {
                        val progress = PurgingService.getProgress(guild!!.id.value.toLong())
                        val userProgress = PurgingService.getUserProgress(guild!!.id.value.toLong())

                        if (progress <= 0) {
                            throw CommandExecutionException("There is no active purge.")
                        }

                        val embed = ApplicationService.embed
                        embed.title = "Current purge"
                        embed.color = EmbedColor.DEFAULT.color

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
                name = "clear"
                description = "Clears the current purge wave."

                action {
                    respond {
                        if (PurgingService.isPurgeActive(guild!!.id.value.toLong())) {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.NEGATIVE.color
                            embed.description = "The purge is already ongoing, it isn't possible to stop it."

                            embeds = mutableListOf(embed)
                            return@respond
                        }

                        PurgingService.clearServer(guild!!.id.value.toLong())

                        val embed = ApplicationService.embed
                        embed.title = "Purge cleared"
                        embed.color = EmbedColor.DEFAULT.color

                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    inner class PurgeArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletion.carryType
        }

        val purgeType by string {
            name = "purge-type"
            description = "The identifier of the purge type"
            maxLength = 30
            autoCompleteCallback = AutoCompletion.purgeType
        }

        val threshold by long {
            name = "threshold"
            description = "The score-threshold."
            minValue = 0
            maxValue = 100
        }
    }
}