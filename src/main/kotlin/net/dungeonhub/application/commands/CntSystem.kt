package net.dungeonhub.application.commands

import com.google.common.collect.Iterables
import dev.kord.common.entity.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.optionalUser
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.components
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.pagination.pages.Page
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasPermission
import net.dungeonhub.application.connection.copy
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.HelpTopic
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.CommandExecutionWarning
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.CntRequestConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.connection.ReputationConnection
import net.dungeonhub.enums.CntRequestType
import net.dungeonhub.i18n.Translations
import net.dungeonhub.model.cnt_request.CntRequestCreationModel
import net.dungeonhub.model.cnt_request.CntRequestModel
import net.dungeonhub.model.discord_user.DiscordUserUpdateModel
import net.dungeonhub.model.reputation.ReputationCreationModel
import net.dungeonhub.model.reputation.ReputationModel
import net.dungeonhub.mojang.connection.MojangConnection
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration
import kotlin.time.toKotlinInstant

@LoadExtension
@OptIn(ExperimentalTime::class)
class CntSystem : Extension() {
    private val logger = LoggerFactory.getLogger(CntSystem::class.java)
    override val name = "cnt-system"

    @OptIn(AlwaysPublicResponse::class)
    override suspend fun setup() {
        CntRequestType.entries.forEach { requestType ->
            event<GuildButtonInteractionCreateEvent> {
                check {
                    failIfNot(requestType.buttonId == event.interaction.componentId)
                }

                action {
                    event.interaction.modal("Crafts And Transfers", requestType.modalId) {
                        actionRow {
                            textInput(TextInputStyle.Short, requestType.descriptionId, "Request")
                        }
                        actionRow {
                            textInput(TextInputStyle.Short, requestType.valueId, "Value")
                        }
                        actionRow {
                            textInput(TextInputStyle.Short, requestType.requirementId, "Craft Requirement")
                        }
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_claim" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequests(event.interaction.message.id.value.toLong())
                    ?.firstOrNull()
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (cntRequest.claimer != null) {
                    event.interaction.respondEphemeral {
                        content = "This request has already been claimed!"
                    }
                    return@action
                }

                val claimerId = event.interaction.user.id.value.toLong()

                if (claimerId == cntRequest.user.id) {
                    event.interaction.respondEphemeral {
                        content = "You cannot claim your own request!"
                    }
                    return@action
                }

                val requestType = cntRequest.requestType

                val serverProperty = ServerProperty.entries.firstOrNull {
                    it.readableName.translate() == "id_cnt_role_requirement_${requestType.valueRange}"
                }

                val requiredRole =
                    serverProperty?.getValue(event.interaction.guildId.value.toLong())?.orElse(null)?.toLongOrNull()

                if (
                    requiredRole != null
                    && !event.interaction.user.roleIds.contains(
                        Snowflake(
                            requiredRole
                        )
                    )
                ) {
                    event.interaction.respondEphemeral {
                        content = "You don't have the required role <@&$requiredRole> to claim requests of that value!"
                    }
                    return@action
                }

                val claimer = DiscordUserConnection.authenticated().getById(claimerId)
                    ?: DiscordUserConnection.authenticated().updateUser(claimerId, DiscordUserUpdateModel(null))
                    ?: throw CommandExecutionException("Couldn't load CNT claimer!")

                val claimedIgn = claimer.minecraftId?.let(MojangConnection::getNameByUUID)
                if (claimedIgn == null) {
                    event.interaction.respondEphemeral {
                        content =
                            "You need to be linked to be able to claim requests! Please check `/help` to see more information about linking."
                    }
                    return@action
                }

                val updateModel = cntRequest.getUpdateModel()
                updateModel.claimer = claimer

                val updatedCntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .updateCntRequest(cntRequest.id, updateModel)
                    ?: throw CommandExecutionException("Couldn't update CNT request!")

                val claimMessage = ApplicationService.embedWithoutTimestamp
                claimMessage.title = "Claimed!"
                claimMessage.description = """ 
                    You have claimed a crafts and transfers request.
                    Do NOT visit the requester. They will visit you shortly.
                    You are not allowed to give collateral.
                """

                event.interaction.respondEphemeral {
                    embeds = mutableListOf(claimMessage)
                }

                try {
                    event.kord.getUser(Snowflake(updatedCntRequest.user.id))?.dm {
                        val embed = embed
                        embed.color(EmbedColor.Positive)
                        embed.description = "## Your CNT request on `${
                            event.interaction.guild.asGuildOrNull()?.name
                        }` has been claimed by <@${claimer.id}> (IGN: ${
                            claimedIgn
                        })!\nPlease visit them ingame by using:\n```\n/visit $claimedIgn```"
                        embed.timestamp = Instant.now().toKotlinInstant()
                        embeds = mutableListOf(embed)
                    }
                } catch (restRequestException: RestRequestException) {
                    // ignore, the user just won't be mentioned in DMs if they don't allow DMs'
                    logger.error(
                        "Error when dming user ${updatedCntRequest.user.id} about their claimed CNT request.",
                        restRequestException
                    )
                }

                val originalMessage = event.interaction.message
                originalMessage.edit {
                    embeds = mutableListOf(ApplicationService.getCntEmbed(updatedCntRequest))

                    components {
                        addClaimedCntButtons()
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_unclaim" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequests(event.interaction.message.id.value.toLong())
                    ?.firstOrNull()
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.claimer?.id
                    && !event.interaction.user.hasPermission(Permission.Administrator)
                ) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is claimed by someone else!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                val unclaimMessage = EmbedBuilder()
                unclaimMessage.title = "Unclaimed!"
                unclaimMessage.description = "The request has been unclaimed. It is now available for others to claim."

                val updateModel = cntRequest.getUpdateModel()
                updateModel.claimer = null

                val updatedCntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .updateCntRequest(cntRequest.id, updateModel)
                    ?: throw CommandExecutionException("Couldn't update CNT request!")

                event.interaction.respondEphemeral {
                    embeds = mutableListOf(unclaimMessage)
                }

                event.interaction.message.edit {
                    embeds = mutableListOf(ApplicationService.getCntEmbed(updatedCntRequest))
                    components {
                        addUnclaimedCntButtons()
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_done" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequests(event.interaction.message.id.value.toLong())
                    ?.firstOrNull()
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.user.id
                    && !event.interaction.user.hasPermission(Permission.Administrator)
                ) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is not yours!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                val updateModel = cntRequest.getUpdateModel()
                updateModel.completed = true

                val updatedCntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .updateCntRequest(cntRequest.id, updateModel)
                    ?: throw CommandExecutionException("Couldn't update CNT request!")

                event.interaction.respondEphemeral {
                    val embed = embed
                    embed.description =
                        "Your CNT request is now marked as completed.\n__Thanks for using our services!__"
                    embeds = mutableListOf(embed)
                }

                event.interaction.message.edit {
                    val embed = ApplicationService.getCntEmbed(updatedCntRequest)
                    embed.color(EmbedColor.Positive)
                    embed.description = "### Craft and Transfers Request completed!"

                    embeds = mutableListOf(embed)
                    components {
                        val claimerId = updatedCntRequest.claimer?.id
                        if (claimerId != null && event.interaction.guild
                                .getMemberOrNull(Snowflake(claimerId))?.let {
                                    isAllowedToGiveReputation(
                                        updatedCntRequest.user.id,
                                        it
                                    )
                                }?.allowedToGive == true
                        ) {
                            addDoneCntButtons()
                        } else {
                            addReputationGivenCntButtons()
                        }
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_reputation_reason" == event.interaction.componentId)
            }


            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequests(event.interaction.message.id.value.toLong())
                    ?.firstOrNull()
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.user.id) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is not yours!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                event.interaction.modal("Enter a reason", "cnt_enter_reason") {
                    actionRow {
                        textInput(TextInputStyle.Short, "reputation_reason", "Reason")
                    }
                }
            }
        }

        event<ModalSubmitInteractionCreateEvent> {
            check {
                failIfNot("cnt_enter_reason" == event.interaction.modalId)
            }

            action {
                val channel = event.interaction.channel
                if (channel !is GuildMessageChannelBehavior) {
                    event.interaction.respondEphemeral {
                        content = "Please use this on a server, DMs are not supported."
                    }
                    return@action
                }

                val messageId = event.interaction.message?.id
                    ?: throw CommandExecutionException("The modal wasn't correctly linked to a message!")

                val cntRequest = CntRequestConnection[channel.guild.id.value.toLong()].authenticated()
                    .findCntRequests(messageId.value.toLong())
                    ?.firstOrNull()
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.user.id) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is not yours!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                val reason = event.interaction.textInputs["reputation_reason"]?.value

                val response = event.interaction.deferEphemeralResponse()

                response.respond {
                    embeds = mutableListOf(
                        addReputation(cntRequest, channel.guild, event.interaction.user, reason)
                    )
                }

                event.interaction.message?.edit {
                    addReputationGivenCntButtons()
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_reputation" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequests(event.interaction.message.id.value.toLong())
                    ?.firstOrNull()
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.user.id) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is not yours!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                val response = event.interaction.deferEphemeralResponse()

                response.respond {
                    embeds = mutableListOf(
                        addReputation(cntRequest, event.interaction.guild, event.interaction.user)
                    )
                }

                event.interaction.message.edit {
                    addReputationGivenCntButtons()
                }
            }
        }

        CntRequestType.entries.forEach { requestType ->
            event<ModalSubmitInteractionCreateEvent> {
                check {
                    failIfNot(requestType.modalId == event.interaction.modalId)
                }

                action {
                    val requesterUser = event.interaction.user

                    val requestDescription = event.interaction.textInputs[requestType.descriptionId]?.value!!
                    val coinValue = event.interaction.textInputs[requestType.valueId]?.value!!
                    val requirement = event.interaction.textInputs[requestType.requirementId]?.value!!

                    val channel = event.interaction.channel
                    if (channel !is GuildMessageChannelBehavior) {
                        event.interaction.respondEphemeral {
                            content = "Please use this on a server, DMs are not supported."
                        }
                        return@action
                    }

                    val channelId =
                        ServerProperty.CNT_MESSAGES_CHANNEL.getValue(channel.guildId.value.toLong()).orElse(null)

                    val responseEmbed = embed
                    responseEmbed.color(EmbedColor.Default)
                    responseEmbed.description =
                        "Thanks for trusting in our service! I'm now trying to send your CNT request into <#$channelId>"

                    val cntEmbed = ApplicationService.getCntEmbed(
                        requestDescription,
                        coinValue,
                        requirement,
                        Clock.System.now(),
                        requesterUser.id.value.toLong()
                    )

                    val response: Message

                    if (channelId != null) {
                        event.interaction.respondEphemeral {
                            embeds = mutableListOf(responseEmbed)
                        }

                        val cntChannel = channel.guild.getChannelOrNull(Snowflake(channelId))
                            ?.asChannelOfOrNull<GuildMessageChannel>() ?: channel

                        response = cntChannel.createMessage {
                            embeds = mutableListOf(cntEmbed)

                            addUnclaimedCntButtons()
                        }
                    } else {
                        response = event.interaction.deferPublicResponse().respond {
                            embeds = mutableListOf(cntEmbed)

                            addUnclaimedCntButtons()
                        }.message
                    }

                    val messageId = response.id

                    val creationModel = CntRequestCreationModel(
                        messageId.value.toLong(),
                        requestType,
                        requesterUser.id.value.toLong(),
                        null,
                        Instant.now(),
                        coinValue,
                        requestDescription,
                        requirement
                    )

                    val embed = CntRequestConnection[channel.guildId.value.toLong()].authenticated()
                        .createCntRequest(creationModel)
                        ?.let { mutableListOf(ApplicationService.getCntEmbed(it)) }
                        ?: ApplicationService.getErrorEmbeds(
                            CommandExecutionException("Could not persist CNT request."),
                            "Could not persist CNT request."
                        )

                    response.edit {
                        embeds = embed
                    }
                }
            }
        }

        publicSlashCommand(::RepArguments) {
            name = Translations.Command.Rep.name
            description = Translations.Command.Rep.description
            allowInDms = false

            action {
                respond {
                    val userToRep = arguments.user.asMemberOrNull(guild!!.id)

                    if (userToRep == null) {
                        addEmbed {
                            description = "That user is not on the server!"
                            color(EmbedColor.Negative)
                        }
                        return@respond
                    }

                    if (userToRep.id == user.id) {
                        addEmbed {
                            description = "You can't give yourself reputation!"
                            color(EmbedColor.Negative)
                        }
                        return@respond
                    }

                    val allowedToRep = isAllowedToGiveReputation(user.id.value.toLong(), userToRep)

                    if (!allowedToRep.allowedToGive) {
                        addEmbed {
                            copy(allowedToRep.response!!)
                        }
                        return@respond
                    }

                    val reputationConnection = ReputationConnection[userToRep].authenticated()

                    val relatedCntRequest = allowedToRep.cntRequest
                        ?: throw CommandExecutionWarning("Couldn't find the related CNT request.")

                    val repCreationModel = ReputationCreationModel(
                        userToRep.id.value.toLong(),
                        user.id.value.toLong(),
                        relatedCntRequest.id,
                        REPUTATION_VALUE,
                        arguments.reason
                    )

                    val reputation = reputationConnection.addReputation(repCreationModel)

                    val totalReputation = reputationConnection.calculateReputation()

                    addEmbed {
                        title = "Rep #${reputation?.id ?: "unknown"} added"
                        description =
                            "Added ${reputation?.amount ?: 0} reputation to <@${reputation?.user?.id}>${if (reputation?.reason != null) " for: ${reputation.reason}" else ""}.\n" +
                                    "They now have $totalReputation total reps."
                        color(EmbedColor.Positive)
                    }

                    val message = guild?.let { getCntRequestMessage(it, relatedCntRequest) }

                    message?.edit {
                        addReputationGivenCntButtons()
                    }
                }
            }
        }

        publicSlashCommand {
            name = Translations.Command.ManageReps.name
            description = Translations.Command.ManageReps.description
            allowInDms = false
            defaultMemberPermissions = Permissions(Permission.ManageMessages)

            publicSubCommand(::RepListArguments) {
                name = Translations.Command.ManageReps.List.name
                description = Translations.Command.ManageReps.List.description

                action {
                    val target: MemberBehavior = arguments.user?.asMemberOrNull(guild!!.id) ?: member!!

                    val reputationConnection = ReputationConnection[target].authenticated()

                    val reputations = reputationConnection.getReputations() ?: emptyList()

                    if (reputations.isEmpty()) {
                        respond {
                            addEmbed {
                                description = "No reputations found for <@${target.id.value}>!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    val totalReputation = reputationConnection.calculateReputation()

                    respondingPaginator {
                        owner = user

                        Iterables.partition(reputations, 10).forEach { reputation ->
                            page(
                                Page {
                                    copy(embed)
                                    color(EmbedColor.Default)
                                    title = "Reputation #${reputation.first().id} to #${reputation.last().id}"
                                    description = reputation.joinToString("\n") {
                                        "${
                                            if (it.active) ":white_check_mark:" else ":x:"
                                        } **Reputation #${it.id}**: ${it.amount} ${if (it.amount == 1) "rep" else "reps"} by <@${it.reputor.id}>${if (it.reason != null) " for **reason**: `${it.reason}`" else ""}"
                                    } + "\n\nTotal amount of reps: ${reputations.size} (${reputations.filter { !it.active }.size} deactivated)\nReputation sum: $totalReputation"
                                }
                            )
                        }
                    }.send()
                }
            }

            publicSubCommand(::RepDeactivateArguments) {
                name = Translations.Command.ManageReps.Deactivate.name
                description = Translations.Command.ManageReps.Deactivate.description

                action {
                    val reputationId = arguments.id

                    val reputation = DiscordServerConnection.authenticated()
                        .getReputation(guild!!.id.value.toLong(), reputationId)
                    if (reputation == null) {
                        respond {
                            addEmbed {
                                description = "Reputation with id $reputationId does not exist!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    val updateModel = reputation.getUpdateModel()
                    updateModel.active = false

                    val updatedReputation = ReputationConnection[member!!].authenticated()
                        .updateReputation(reputationId, updateModel)
                    if (updatedReputation == null) {
                        respond {
                            addEmbed {
                                description = "Couldn't update reputation #$reputationId."
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    respond {
                        addEmbed {
                            copy(getReputationEmbed(updatedReputation))
                        }
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("help-rep" == event.interaction.componentId)
            }

            action {
                event.interaction.respondEphemeral {
                    embeds = mutableListOf(
                        HelpTopic.generateHelpEmbed(
                            HelpTopic.REPUTATION,
                            event.interaction.user,
                            event.interaction.getGuildOrNull()
                        )
                    )
                }
            }
        }
    }

    // TODO merge this with the response of the /rep slash command --> if possible
    suspend fun addReputation(
        cntRequest: CntRequestModel,
        guild: GuildBehavior,
        executor: UserBehavior,
        reason: String? = null
    ): EmbedBuilder {
        val userToRep = cntRequest.claimer?.id?.let { guild.getMemberOrNull(Snowflake(it)) }

        if (userToRep == null) {
            return buildEmbed {
                description = "That user is not on the server!"
                color(EmbedColor.Negative)
            }
        }

        val allowedToRep = isAllowedToGiveReputation(executor.id.value.toLong(), userToRep)

        if (!allowedToRep.allowedToGive) {
            return allowedToRep.response!!
        }

        val reputationConnection = ReputationConnection[userToRep].authenticated()

        val repCreationModel = ReputationCreationModel(
            userToRep.id.value.toLong(),
            executor.id.value.toLong(),
            cntRequest.id,
            REPUTATION_VALUE,
            reason
        )

        val reputation = reputationConnection.addReputation(repCreationModel)

        val totalReputation = reputationConnection.calculateReputation()

        return buildEmbed {
            title = "Rep #${reputation?.id ?: "unknown"} added"
            description =
                "Added ${reputation?.amount ?: 0} reputation to <@${reputation?.user?.id}>${if (reputation?.reason != null) " for: ${reputation.reason}" else ""}.\n" +
                        "They now have $totalReputation total reps."
            color(EmbedColor.Positive)
        }
    }

    fun getReputationEmbed(reputation: ReputationModel): EmbedBuilder {
        val embed = embed
        embed.title = "Reputation #${reputation.id}"

        embed.field("User", true) { "<@${reputation.user.id}>" }
        embed.field("Reputor", true) { "<@${reputation.reputor.id}>" }
        embed.field("Amount", true) { reputation.amount.toString() }
        reputation.reason?.let { embed.field("Reason", true) { it } }
        embed.field("Active", true) { reputation.active.toString() }

        embed.color(EmbedColor.Default)
        embed.timestamp = reputation.time.toKotlinInstant()

        return embed
    }

    suspend fun getCntRequestMessage(guild: GuildBehavior, cntRequest: CntRequestModel): MessageBehavior? {
        return ServerProperty.CNT_MESSAGES_CHANNEL.getValue(guild.id.value.toLong()).orElse(null)
            ?.let {
                guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(it))
            }?.getMessageOrNull(Snowflake(cntRequest.messageId))
    }

    class ReputationValidityResult(
        val allowedToGive: Boolean,
        val response: EmbedBuilder? = null,
        val cntRequest: CntRequestModel? = null
    )

    fun isAllowedToGiveReputation(userId: Long, target: MemberBehavior): ReputationValidityResult {
        val timeout = Instant.now().minusSeconds(reputationTimeout.inWholeSeconds)

        val reputationConnection = ReputationConnection[target].authenticated()

        val lastRep = reputationConnection.getReputations()
            ?.filter { it.reputor.id == userId }
            ?.filter { it.time.isAfter(timeout) }
            ?.maxByOrNull { it.time }

        if (lastRep != null) {
            val ready = java.time.Duration.between(
                Instant.now(),
                lastRep.time.plusSeconds(reputationTimeout.inWholeSeconds)
            ).withNanos(0).toKotlinDuration()

            return ReputationValidityResult(
                false, buildEmbed {
                    description = "You already added the rep #${lastRep.id} to <@${lastRep.user.id}>.\n" +
                            (if (lastRep.reason != null) "The last reputation had the reason: ${lastRep.reason}\n" else "") +
                            "The next rep will be available in: $ready"
                    color(EmbedColor.Negative)
                    timestamp = lastRep.time.toKotlinInstant()
                }
            )
        }

        val relatedCntRequest = CntRequestConnection[target.guild.id.value.toLong()].authenticated()
            .findCntRequestsByUser(userId)
            ?.filter { it.completed }
            ?.filter { it.claimer?.id == target.id.value.toLong() }
            ?.filter { it.time.isAfter(timeout) }
            ?.maxByOrNull { it.time }

        if (relatedCntRequest == null) {
            return ReputationValidityResult(
                false, buildEmbed {
                    description =
                        "<@${target.id.value}> has not completed a crafts and transfers request for you in the past $reputationTimeout!"
                    color(EmbedColor.Negative)
                }
            )
        }

        return ReputationValidityResult(true, cntRequest = relatedCntRequest)
    }

    private fun MessageBuilder.addClaimedCntButtons() {
        actionRow {
            interactionButton(ButtonStyle.Secondary, "cnt_unclaim") {
                label = "Unclaim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_done") {
                label = "Done"
            }
        }
    }

    private fun MessageBuilder.addUnclaimedCntButtons() {
        actionRow {
            interactionButton(ButtonStyle.Primary, "cnt_claim") {
                label = "Claim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_done") {
                label = "Done"
            }
        }
    }

    private fun MessageBuilder.addDoneCntButtons() {
        actionRow {
            interactionButton(ButtonStyle.Primary, "cnt_reputation_reason") {
                label = "Give reputation with reason"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_reputation") {
                label = "Give reputation"
            }
        }
    }

    private fun MessageBuilder.addReputationGivenCntButtons() {
        components = mutableListOf()
    }

    private class RepArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The discord user to rep.".toKey()
        }

        val reason by optionalString {
            name = "reason".toKey()
            description =
                "You can provide an additional reason for the rep, e.g. a certain service they helped you with.".toKey()
        }
    }

    private class RepListArguments : Arguments() {
        val user by optionalUser {
            name = "user".toKey()
            description = "The discord user to list reputations for.".toKey()
        }
    }

    private class RepDeactivateArguments : Arguments() {
        val id by long {
            name = "id".toKey()
            description = "The id of the reputation to deactivate.".toKey()
        }
    }

    companion object {
        private const val REPUTATION_VALUE = 1
        private val reputationTimeout = 3.days
    }
}