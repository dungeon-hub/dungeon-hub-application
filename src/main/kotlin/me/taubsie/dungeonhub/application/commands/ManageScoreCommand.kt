package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.ServerProperty
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.exceptions.MissingPermissionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.application.service.LeaderboardService
import me.taubsie.dungeonhub.application.service.PermissionService
import me.taubsie.dungeonhub.common.enums.ScoreResetType
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.score.ScoreModel
import me.taubsie.dungeonhub.common.model.score.ScoreUpdateModel

@LoadExtension
class ManageScoreCommand : Extension() {
    override val name = "manage-score-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "manage-score"
            description = "Use this to manage the score of carriers."
            defaultMemberPermissions = Permissions(Permission.ManageMessages)
            allowInDms = false

            publicSubCommand(::ManageScoreArguments) {
                name = "add"
                description = "Add score."

                action {
                    respond {
                        addRemove(event, guild, arguments, false)()
                    }
                }
            }

            publicSubCommand(::ManageScoreArguments) {
                name = "remove"
                description = "Remove score."

                action {
                    respond {
                        addRemove(event, guild, arguments, true)()
                    }
                }
            }

            publicSubCommand(::ResetScoreArguments) {
                name = "reset"
                description = "Reset score for a given carry type."

                check {
                    check {
                        hasPermission(Permission.Administrator)
                    }
                }

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(arguments.carryType)
                                .orElseThrow { InvalidOptionException("carry-type") }

                        val resetModel =
                            ScoreConnection.getInstance(
                                carryType
                            )
                                .resetScore(arguments.resetType)
                                .orElseThrow { CommandExecutionException("Error while getting a response when resetting score.") }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.INFORMATION.color
                        embed.title = "Score for ${carryType.displayName} successfully reset!"
                        embed.description = when (arguments.resetType) {
                            ScoreResetType.Default -> "Default score of ${resetModel.defaultCount} users were reset. ${
                                if (resetModel.eventCount != 0L) "\nSomehow also ${resetModel.eventCount} users got their event score reset?" else ""
                            }"

                            ScoreResetType.Event -> "Event score of ${resetModel.eventCount} users were reset. ${
                                if (resetModel.defaultCount != 0L) "\nSomehow also ${resetModel.defaultCount} users got their default score reset?" else ""
                            }"

                            ScoreResetType.Both -> "Default score of ${resetModel.defaultCount} users and event score of ${resetModel.eventCount} were reset."
                        }

                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    private fun addRemove(
        event: ChatInputCommandInteractionCreateEvent,
        guild: GuildBehavior?,
        arguments: ManageScoreArguments,
        remove: Boolean
    ): suspend FollowupMessageCreateBuilder.() -> Unit = {
        if (!PermissionService.mayManageServices(event.interaction.user.asMember(guild!!.id))) {
            throw MissingPermissionException()
        }

        val carryType =
            CarryTypeConnection.getInstance(guild.id.value.toLong())
                .getByIdentifier(arguments.carryType)
                .orElse(null)

        if (carryType == null) {
            throw InvalidOptionException("carry-type")
        }

        val score = if (remove) -arguments.amount else arguments.amount

        val updatedScores =
            ScoreConnection.getInstance(carryType)
                .updateScores(ScoreUpdateModel(arguments.user.id.value.toLong(), score))
                .orElse(listOf())

        val updatedScore = updatedScores.stream()
            .filter { scoreModel: ScoreModel -> scoreModel.scoreType == ScoreType.DEFAULT }
            .map { obj: ScoreModel -> obj.scoreAmount }
            .findFirst()
            .orElse(0L)

        val logs = ServerProperty.SCORE_LOGS_CHANNEL
            .getValue(guild.id.value.toLong())
            .map { id -> runBlocking { guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id)) } }

        logs.ifPresent { serverTextChannel ->
            runBlocking {
                serverTextChannel.createMessage {
                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.INFORMATION.color
                    embed.title = "Score-Management"
                    embed.description =
                        "${event.interaction.user.mention} edited the ${carryType.displayName}-score of ${arguments.user.mention}.\nThey ${(if (remove) "removed" else "added")} ${arguments.amount} score, the user now has $updatedScore score."

                    embeds = mutableListOf(embed)
                }
            }
        }

        LeaderboardService.refreshLeaderboard()

        val embed = ApplicationService.embed
        embed.color = EmbedColor.INFORMATION.color
        embed.title = "Score-Management"
        embed.description =
            "${event.interaction.user.mention}, the user ${arguments.user.mention} now has $updatedScore ${carryType.displayName}-score.\nYou ${(if (remove) "removed" else "added")} ${arguments.amount} of that score."

        embeds = mutableListOf(embed)
    }

    inner class ManageScoreArguments : Arguments() {
        val user by user {
            name = "user"
            description = "The user to manage score."
        }

        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val amount by long {
            name = "amount"
            description = "The amount of score to add/remove."
            maxValue = 10000
            minValue = 0
        }
    }

    inner class ResetScoreArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val resetType by enumChoice<ScoreResetType> {
            name = "reset-type"
            description = "Choose which score should be reset"
            typeName = "ScoreResetType"
        }
    }
}