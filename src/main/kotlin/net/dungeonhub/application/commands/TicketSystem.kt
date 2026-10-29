package net.dungeonhub.application.commands

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.kord.common.entity.*
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.channel.*
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.request.KtorRequestException
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.hasPermission
import dev.kordex.core.utils.scheduling.Scheduler
import dev.kordex.i18n.toKey
import io.ktor.http.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dungeonhub.application.connection.withNanos
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.listener.ticket.*
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.application.misc.TicketPlaceholders
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.TicketConnection
import net.dungeonhub.enums.DiscordChannelPermission
import net.dungeonhub.enums.TicketAction
import net.dungeonhub.enums.TicketPermissionCandidate
import net.dungeonhub.enums.TicketPhase
import net.dungeonhub.enums.TicketState
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.i18n.Translations
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

// TODO command: /ticket escalate --> this should move the ticket to a different panel and then update the ticket status
@LoadExtension
class TicketSystem : Extension() {
    override val name = "ticket-system"

    override suspend fun setup() {
        scheduler = DhScheduler()

        scheduler.schedule(5.minutes, startNow = true, name = "Delayed Ticket Name Updater", repeat = true) {
            scheduledTicketRenames.toList().forEach { (guildId, channelId) ->
                val channel = kord.getChannel(Snowflake(channelId))?.asChannelOfOrNull<TextChannel>()
                if (channel == null) {
                    scheduledTicketRenames.removeIf { it.second == channelId }
                    return@forEach
                }

                val ticket = DiscordServerConnection.authenticated().findTickets(guildId, channelId = channelId)?.firstOrNull()
                if (ticket == null) {
                    scheduledTicketRenames.removeIf { it.second == channelId }
                    return@forEach
                }

                val member = kord.getGuildOrNull(Snowflake(ticket.ticketPanel.discordServer.id))?.getMemberOrNull(Snowflake(ticket.user.id))
                if(member == null) {
                    scheduledTicketRenames.remove(guildId to channelId)
                    return@forEach
                }

                if (updateTicketName(ticket, member, channel) == null) {
                    scheduledTicketRenames.removeIf { it.second == channelId }
                }
            }

            val now = java.time.Instant.now().toKotlinInstant()
            ticketRenames.entries.removeIf { (_, renames) ->
                renames.none { it > now.minus(15.minutes) }
            }
        }

        event<MessageCreateEvent> {
            check {
                failIfNot(
                    event.message.type == MessageType.ChannelPinnedMessage && event.message.author?.isSelf == true
                )
            }

            action {
                event.message.delete("I'm deleting my own pin notifications to keep tickets clean :)")
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.componentId == "ticket-guild-status")
            }

            action {
                val response = event.interaction.deferEphemeralResponse()

                response.respond {
                    val ticket = DiscordServerConnection.authenticated()
                        .findTickets(event.interaction.guildId.value.toLong(), event.interaction.channelId.value.toLong())
                        ?.firstOrNull()

                    if(ticket == null) {
                        addEmbed {
                            description = "Couldn't load the ticket!"
                            color(EmbedColor.Negative)
                        }
                        return@respond
                    }

                    val uuid = ticket.user.minecraftId

                    if(uuid == null) {
                        addEmbed {
                            description = "The ticket user currently isn't linked!"
                            color(EmbedColor.Negative)
                        }
                        return@respond
                    }

                    val ign = MojangConnection.getNameByUUID(uuid)

                    val hypixelApiConnection = HypixelApiConnection().withCacheExpiration(5)

                    val guild = hypixelApiConnection.getPlayerGuild(uuid).valueOrNull

                    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                        .withZone(ZoneId.systemDefault())

                    addEmbed {
                        title = "Guild of $ign"
                        color(if(guild != null) EmbedColor.Positive else EmbedColor.Negative)
                        description = if(guild != null) {
                            "$ign is currently in guild `${guild.displayName}${
                                if(guild.tag != null) " [${guild.tag}]" else ""
                            }`, since ${guild.members.firstOrNull { it.uuid == uuid }?.joinedAt?.let { formatter.format(it) }}"
                        } else {
                            "$ign isn't in any guild!"
                        }
                        thumbnail {
                            url = "https://visage.surgeplay.com/face/$uuid"
                        }
                    }
                }
            }
        }

        publicSlashCommand {
            name = Translations.Command.Ticket.name
            description = Translations.Command.Ticket.description
            allowInDms = false

            ephemeralSubCommand {
                name = Translations.Command.Ticket.Close.name
                description = Translations.Command.Ticket.Close.description

                action {
                    val ticket = DiscordServerConnection.authenticated().findTickets(guild!!.id.value.toLong(), channelId = event.interaction.channelId.value.toLong())?.firstOrNull()

                    if(ticket == null) {
                        respond {
                            addEmbed {
                                description = "This isn't a ticket channel!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    if(ticket.state == TicketState.Closed || ticket.state == TicketState.Deleted) {
                        respond {
                            addEmbed {
                                description = "This ticket is already closed!"
                                color(EmbedColor.Negative)
                            }

                            actionRow {
                                getControlButtons().forEach {
                                    it()
                                }
                            }
                        }
                        return@action
                    }

                    // without closing, the ticket is deleted directly
                    val closeAction = if (ticket.ticketPanel.closeable) TicketAction.Close else TicketAction.Delete

                    if(!member!!.asMember().isAllowedTo(closeAction, ticket)) {
                        respond {
                            addEmbed {
                                description = "You're not allowed to close this ticket!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    if(!ticket.ticketPanel.closeable) {
                        respond {
                            embeds = mutableListOf(TicketDeleteListener.deleteTicket(ticket, member!!.asMember(), event.interaction.channel.asChannelOf<TextChannel>()))
                        }
                        return@action
                    }

                    if(ticket.ticketPanel.closeConfirmation) {
                        respond {
                            addEmbed {
                                description = "Please confirm that you actually want to close this ticket."
                                color(EmbedColor.Information)
                            }

                            components {
                                ephemeralButton {
                                    label = "Confirm".toKey()
                                    style = ButtonStyle.Success

                                    action {
                                        val updatedTicket = TicketConnection[ticket.ticketPanel.discordServer, ticket.ticketPanel].authenticated().getById(ticket.id)

                                        if(updatedTicket == null) {
                                            respond {
                                                embeds = mutableListOf(buildEmbed {
                                                    description = "Couldn't load ticket info!"
                                                    color(EmbedColor.Negative)
                                                })
                                            }
                                            return@action
                                        }

                                        if(updatedTicket.state == TicketState.Closed || updatedTicket.state == TicketState.Deleted) {
                                            respond {
                                                addEmbed {
                                                    description = "This ticket is already closed!"
                                                    color(EmbedColor.Negative)
                                                }
                                            }
                                            return@action
                                        }

                                        val embed = TicketCloseListener.closeTicket(member!!, channel.asChannelOf(), updatedTicket)

                                        respond {
                                            embeds = mutableListOf(embed)
                                        }
                                    }
                                }
                            }
                        }
                        return@action
                    }

                    val embed = TicketCloseListener.closeTicket(member!!, event.interaction.channel.asChannelOf(), ticket)

                    respond {
                        embeds = mutableListOf(embed)
                    }
                }
            }

            ephemeralSubCommand {
                name = Translations.Command.Ticket.Claim.name
                description = Translations.Command.Ticket.Claim.description

                action {
                    val ticket = DiscordServerConnection.authenticated().findTickets(guild!!.id.value.toLong(), channelId = event.interaction.channelId.value.toLong())?.firstOrNull()

                    val ticketChannel = channel.asChannelOfOrNull<TextChannel>()

                    val claimAction = resolveTicketClaimAction(
                        ticketExists = ticket != null && ticketChannel != null,
                        claimable = ticket?.ticketPanel?.claimable == true,
                        state = ticket?.state,
                        hasClaimer = ticket?.claimer != null,
                    )

                    val embed = when (claimAction) {
                        TicketClaimAction.NotATicket -> buildEmbed {
                            description = "This isn't a ticket channel!"
                            color(EmbedColor.Negative)
                        }
                        TicketClaimAction.NotClaimable -> buildEmbed {
                            description = "This panel doesn't allow ticket claiming!"
                            color(EmbedColor.Negative)
                        }
                        TicketClaimAction.Deleted -> buildEmbed {
                            description = "This ticket is already deleted!"
                            color(EmbedColor.Negative)
                        }
                        TicketClaimAction.NotOpen -> buildEmbed {
                            description = "This ticket isn't open!"
                            color(EmbedColor.Negative)
                        }
                        TicketClaimAction.Claim -> TicketClaimListener.claimTicket(member!!.asMember(), ticket!!, ticketChannel!!)
                        TicketClaimAction.Unclaim -> TicketClaimListener.unclaimTicket(member!!.asMember(), ticket!!, ticketChannel!!)
                    }

                    respond {
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::TicketAddArguments) {
                name = Translations.Command.Ticket.Add.name
                description = Translations.Command.Ticket.Add.description

                action {
                    val ticket = DiscordServerConnection.authenticated().findTickets(guild!!.id.value.toLong(), channelId = event.interaction.channelId.value.toLong())?.firstOrNull()

                    val ticketChannel = channel.asChannelOfOrNull<TextChannel>()

                    if(ticket == null || ticketChannel == null) {
                        respond {
                            addEmbed {
                                description = "This isn't a ticket channel!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    if(ticket.state == TicketState.Closed) {
                        respond {
                            addEmbed {
                                description = "This ticket is already closed!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    if(ticket.state == TicketState.Deleted) {
                        respond {
                            addEmbed {
                                description = "This ticket is already deleted!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    if(!member!!.asMember().isAllowedTo(TicketAction.AddUser, ticket)) {
                        respond {
                            addEmbed {
                                description = "You're not allowed to add users to this ticket!"
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    // added users get the same channel permissions as the claimer
                    val claimerPermissions = ticket.ticketPanel.permissions.channelPermissions(
                        TicketPhase.of(ticket.state),
                        TicketPermissionCandidate.TicketClaimer
                    )

                    if (claimerPermissions.isNotEmpty()) {
                        ticketChannel.edit {
                            keepOverwrites(ticketChannel)
                            addChannelPermissions(arguments.target.id.value.toLong(), OverwriteType.Member, claimerPermissions)
                        }
                    }

                    logTicketAction(guild!!, ticket) {
                        description = "${member!!.mention} added ${arguments.target.mention} to ticket #${ticket.id}."
                    }

                    respond {
                        addEmbed {
                            description = "Added ${arguments.target.mention} to the ticket!\n" +
                                    "-# Note that currently the user will lose access once the ticket changes state, such as being claimed/unclaimed or closed."
                            color(EmbedColor.Positive)
                        }
                    }
                }
            }
        }
    }

    class TicketAddArguments : Arguments() {
        val target by user {
            name = "user".toKey()
            description = "The user to add to the ticket.".toKey()
        }
    }

    override suspend fun unload() {
        scheduler.cancel("Extension shutting down.")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TicketSystem::class.java)
        lateinit var scheduler: Scheduler
        val ticketRenames = ConcurrentHashMap<Long, List<Instant>>()
        val scheduledTicketRenames = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()

        suspend fun replacePlaceholders(string: String, placeholders: TicketPlaceholders): String {
            val replacements = placeholders.replacements

            val regex = "(\\{[^}]+})"
            val result = StringBuilder()
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(string)

            while (matcher.find()) {
                val argument = matcher.group(1)

                val repString = replacements[argument.substring(1, argument.length - 1)]?.invoke()
                if (repString != null) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(repString))
                }
            }
            matcher.appendTail(result)

            return result.toString().trim()
        }

        suspend fun replacePlaceholders(element: JsonElement, placeholders: TicketPlaceholders): JsonElement {
            return when (element) {
                is JsonObject -> {
                    val obj = JsonObject()
                    for ((key, value) in element.entrySet()) {
                        obj.add(key, replacePlaceholders(value, placeholders))
                    }
                    obj
                }

                is JsonArray -> {
                    val array = JsonArray()
                    for (value in element) {
                        array.add(replacePlaceholders(value, placeholders))
                    }
                    array
                }

                is JsonPrimitive -> {
                    if (element.isString) {
                        JsonPrimitive(replacePlaceholders(element.asString, placeholders))
                    } else {
                        element
                    }
                }

                else -> element
            }
        }

        fun getControlButtons(): List<ActionRowBuilder.() -> Unit> {
            return listOf({
                interactionButton(ButtonStyle.Secondary, "transcript-ticket") {
                    label = "Transcript"
                }
            }, {
                interactionButton(ButtonStyle.Success, "open-ticket") {
                    label = "Open"
                }
            }, {
                interactionButton(ButtonStyle.Danger, "delete-ticket") {
                    label = "Delete"
                }
            })
        }

        fun getClaimedButtons(): List<ActionRowBuilder.() -> Unit> {
            return listOf({
                interactionButton(ButtonStyle.Secondary, "claim-ticket") {
                    label = "Unclaim"
                }
            })
        }

        // TODO use this method with a discord timestamp embed rather
        suspend fun updateTicketName(ticket: TicketModel, member: MemberBehavior, textChannel: TextChannel): Duration? {
            val newName = buildTicketName(ticket.ticketPanel, ticket, member.asMember(), textChannel) ?: return null

            if (newName != textChannel.name) {
                val channelId = textChannel.id.value.toLong()
                val now = java.time.Instant.now().toKotlinInstant()

                val recentRenames = ticketRenames.getOrDefault(channelId, emptyList())
                    .filter { it > now.minus(15.minutes) }

                if (recentRenames.size >= 2) {
                    scheduledTicketRenames.add(textChannel.guildId.value.toLong() to channelId)
                    return ((recentRenames.first() + 15.minutes) - now).withNanos(0)
                }

                try {
                    textChannel.edit {
                        name = newName
                    }

                    ticketRenames[channelId] = recentRenames + now
                } catch (ktorRequestException: KtorRequestException) {
                    // We assume that the ratelimit didn't actually expire or is still in place after restarting
                    if(ktorRequestException.status.code == HttpStatusCode.TooManyRequests.value) {
                        if(recentRenames.isEmpty()) {
                            ticketRenames[channelId] = (recentRenames + now) + now

                            return 15.minutes
                        } else {
                            ticketRenames[channelId] = recentRenames + now

                            return ((recentRenames.first() + 15.minutes) - now).withNanos(0)
                        }
                    } else {
                        logger.error("Error while updating a ticket name.", ktorRequestException)
                    }
                }
            }

            return null
        }

        suspend fun buildTicketName(ticketPanel: TicketPanelModel, ticket: TicketModel, member: Member, ticketChannel: TextChannel?): String? {
            val placeholders = TicketPlaceholders(ticketPanel, ticket, member, ticketChannel)

            val channelName = if (ticket.state == TicketState.Open && ticket.claimer != null) {
                ticketPanel.claimedChannelName
            } else {
                when (ticket.state) {
                    TicketState.Creating, TicketState.Open -> ticketPanel.openChannelName
                    TicketState.Closed, TicketState.Deleted -> ticketPanel.closedChannelName
                }
            }

            return channelName?.let { replacePlaceholders(it, placeholders) }
        }

        fun PermissionOverwritesBuilder.updateTicketPermissions(ticketPanel: TicketPanelModel, ticket: TicketModel) {
            val phase = TicketPhase.of(ticket.state)
            val permissions = ticketPanel.permissions

            // while a ticket is claimed, the support team doesn't get its overwrite
            if (ticket.claimer == null) {
                for (supportRole in ticketPanel.supportRoles) {
                    addChannelPermissions(supportRole.id, OverwriteType.Role, permissions.channelPermissions(phase, TicketPermissionCandidate.SupportTeam))
                }
            }

            for (additionalRole in ticketPanel.additionalRoles) {
                addChannelPermissions(additionalRole.id, OverwriteType.Role, permissions.channelPermissions(phase, TicketPermissionCandidate.AdditionalRoles))
            }

            // the creator and the claimer can be the same member, so their permissions are merged
            val memberPermissions = mutableMapOf<Long, MutableMap<DiscordChannelPermission, Boolean>>()

            memberPermissions.getOrPut(ticket.user.id) { mutableMapOf() }
                .mergeChannelPermissions(permissions.channelPermissions(phase, TicketPermissionCandidate.TicketCreator))

            ticket.claimer?.let { claimer ->
                memberPermissions.getOrPut(claimer.id) { mutableMapOf() }
                    .mergeChannelPermissions(permissions.channelPermissions(phase, TicketPermissionCandidate.TicketClaimer))
            }

            for ((memberId, channelPermissions) in memberPermissions) {
                addChannelPermissions(memberId, OverwriteType.Member, channelPermissions)
            }

            addChannelPermissions(ticketPanel.discordServer.id, OverwriteType.Role, permissions.channelPermissions(phase, TicketPermissionCandidate.Everyone))
        }

        private fun MutableMap<DiscordChannelPermission, Boolean>.mergeChannelPermissions(channelPermissions: Map<DiscordChannelPermission, Boolean>) {
            for ((permission, allowed) in channelPermissions) {
                merge(permission, allowed) { existing, new -> existing || new }
            }
        }

        fun PermissionOverwritesBuilder.addChannelPermissions(target: Long, type: OverwriteType, channelPermissions: Map<DiscordChannelPermission, Boolean>) {
            if (channelPermissions.isEmpty()) return

            val overwrite = PermissionOverwriteBuilder(type, Snowflake(target))
            overwrite.allowed = Permissions(channelPermissions.filterValues { it }.keys.map { it.toKordPermission() })
            overwrite.denied = Permissions(channelPermissions.filterValues { !it }.keys.map { it.toKordPermission() })
            addOverwrite(overwrite.toOverwrite())
        }

        fun DiscordChannelPermission.toKordPermission(): Permission = when (this) {
            DiscordChannelPermission.ViewChannel -> Permission.ViewChannel
            DiscordChannelPermission.SendMessages -> Permission.SendMessages
            DiscordChannelPermission.ManageMessages -> Permission.ManageMessages
            DiscordChannelPermission.EmbedLinks -> Permission.EmbedLinks
            DiscordChannelPermission.AttachFiles -> Permission.AttachFiles
            DiscordChannelPermission.ReadMessageHistory -> Permission.ReadMessageHistory
            DiscordChannelPermission.MentionEveryone -> Permission.MentionEveryone
        }

        fun TextChannelModifyBuilder.keepOverwrites(textChannel: TextChannel) {
            textChannel.permissionOverwrites.forEach { overwrite ->
                val newOverwrite = PermissionOverwriteBuilder(overwrite.type, overwrite.target)
                newOverwrite.allowed = overwrite.allowed
                newOverwrite.denied = overwrite.denied
                addOverwrite(newOverwrite.toOverwrite())
            }
        }

        fun getCategory(categories: List<Long>): Long? {
            // TODO implement lookup for a category with enough space
            return categories.getOrNull(0)
        }

        suspend fun Member.isAllowedTo(action: TicketAction, ticket: TicketModel): Boolean {
            if (hasPermission(Permission.Administrator) || hasPermission(Permission.ManageChannels)) return true

            return ticket.ticketPanel.permissions.isAllowed(TicketPhase.of(ticket.state), getTicketPermissionCandidates(ticket), action)
        }

        fun Member.getTicketPermissionCandidates(ticket: TicketModel): Set<TicketPermissionCandidate> = buildSet {
            add(TicketPermissionCandidate.Everyone)

            if (ticket.ticketPanel.supportRoles.any { roleIds.contains(Snowflake(it.id)) }) {
                add(TicketPermissionCandidate.SupportTeam)
            }

            if (ticket.ticketPanel.additionalRoles.any { roleIds.contains(Snowflake(it.id)) }) {
                add(TicketPermissionCandidate.AdditionalRoles)
            }

            if (ticket.user.id == id.value.toLong()) {
                add(TicketPermissionCandidate.TicketCreator)
            }

            if (ticket.claimer?.id == id.value.toLong()) {
                add(TicketPermissionCandidate.TicketClaimer)
            }
        }

        fun logTicketAction(guild: GuildBehavior, ticket: TicketModel, addDefaultFields: Boolean = true, builder: suspend EmbedBuilder.() -> Unit) {
            scheduler.launch {
                val ticketLogChannel = ServerProperty.TICKET_LOGS_CHANNEL.getValue(guild.id)?.let {
                    guild.getChannelOfOrNull<TextChannel>(Snowflake(it))
                } ?: return@launch

                val embed = buildEmbed {
                    color(EmbedColor.Log)
                }

                if(addDefaultFields) {
                    embed.field("Panel", true) { ticket.ticketPanel.displayName ?: ticket.ticketPanel.name }
                    embed.field("Ticket Name", true) {
                        ticket.channel?.id?.let { guild.getChannelOrNull(Snowflake(it)) }?.name
                            ?: ticket.channel?.name
                            ?: "<#${ticket.channel?.id}>"
                    }
                }

                builder(embed)
                try {
                    ticketLogChannel.createMessage {
                        embeds = mutableListOf(embed)
                    }
                } catch (_: RestRequestException) {
                    // ignore, just a mere log statement
                }
            }
        }
    }
}