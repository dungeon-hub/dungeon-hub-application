package net.dungeonhub.application.commands

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.channel.PermissionOverwriteBuilder
import dev.kord.rest.builder.channel.PermissionOverwritesBuilder
import dev.kord.rest.builder.channel.addMemberOverwrite
import dev.kord.rest.builder.channel.addRoleOverwrite
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.hasPermission
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.TicketPlaceholders
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.enums.TicketPermissionCandidate
import net.dungeonhub.enums.TicketPermissionType
import net.dungeonhub.enums.TicketState
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.time.ExperimentalTime

// TODO command: /ticket escalate --> this should move the ticket to a different panel and then update the ticket status
@OptIn(ExperimentalTime::class)
@LoadExtension
class TicketSystem : Extension() {
    override val name = "ticket-system"

    override suspend fun setup() {
        scheduler = Scheduler()

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

                    val guild = hypixelApiConnection.getPlayerGuild(uuid)

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
    }

    override suspend fun unload() {
        scheduler.cancel("Extension shutting down.")
    }

    companion object {
        lateinit var scheduler: Scheduler

        fun replacePlaceholders(string: String, placeholders: TicketPlaceholders): String {
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

        fun replacePlaceholders(element: JsonElement, placeholders: TicketPlaceholders): JsonElement {
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

        fun buildTicketName(ticketPanel: TicketPanelModel, ticket: TicketModel, member: Member, ticketChannel: TextChannel?): String? {
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
            for (entry in ticketPanel.permissions.entries) {
                when (entry.key) {
                    TicketPermissionCandidate.SupportTeam -> {
                        for (supportRole in ticketPanel.supportRoles) {
                            if (ticket.claimer == null) {
                                addRoleOverwrite(Snowflake(supportRole.id)) {
                                    addOverwritePermissions(entry.value.entries)
                                }
                            }
                        }
                    }

                    TicketPermissionCandidate.AdditionalRoles -> {
                        for (additionalRole in ticketPanel.additionalRoles) {
                            addRoleOverwrite(Snowflake(additionalRole.id)) {
                                addOverwritePermissions(entry.value.entries)
                            }
                        }
                    }

                    TicketPermissionCandidate.TicketCreator -> {
                        if (ticket.state != TicketState.Closed) { // TODO add a config for this
                            addMemberOverwrite(Snowflake(ticket.user.id)) {
                                addOverwritePermissions(entry.value.entries)
                            }
                        }
                    }

                    TicketPermissionCandidate.TicketClaimer -> {
                        if (ticket.claimer != null) {
                            addMemberOverwrite(Snowflake(ticket.claimer!!.id)) {
                                addOverwritePermissions(entry.value.entries)
                            }
                        }
                    }

                    TicketPermissionCandidate.Everyone -> {
                        addRoleOverwrite(Snowflake(ticketPanel.discordServer.id)) {
                            addOverwritePermissions(entry.value.entries)
                        }
                    }
                }
            }
        }

        fun PermissionOverwriteBuilder.addOverwritePermissions(entries: Set<Map.Entry<TicketPermissionType, Permissions>>) {
            for (permissionEntry in entries) {
                when (permissionEntry.key) {
                    TicketPermissionType.Allowed -> allowed = permissionEntry.value
                    TicketPermissionType.Denied -> denied = permissionEntry.value
                }
            }
        }

        fun getCategory(categories: List<Long>): Long? {
            // TODO implement lookup for a category with enough space
            return categories.getOrNull(0)
        }

        // TODO this was initially just for closing the ticket - does that also count for reopening the ticket etc?
        // TODO is there any other situation in which some user might be allowed / disallowed to close a ticket?
        suspend fun Member.isAllowedToChangeState(ticket: TicketModel): Boolean {
            if (hasPermission(Permission.Administrator) || hasPermission(Permission.ManageChannels)) return true

            if (ticket.ticketPanel.supportRoles.any { roleIds.contains(Snowflake(it.id)) }
                || ticket.ticketPanel.additionalRoles.any { roleIds.contains(Snowflake(it.id)) }) {
                return true
            }

            // TODO here, we assume that the ticket creator is allowed to close the ticket - that should be a setting
            return ticket.user.id == id.value.toLong()
        }

        // TODO check
        suspend fun Member.isAllowedToClaim(ticket: TicketModel): Boolean {
            if (hasPermission(Permission.Administrator) || hasPermission(Permission.ManageChannels)) return true

            if (ticket.ticketPanel.supportRoles.any { roleIds.contains(Snowflake(it.id)) }
                || ticket.ticketPanel.additionalRoles.any { roleIds.contains(Snowflake(it.id)) }) {
                return true
            }

            return false
        }

        // TODO check
        suspend fun Member.isAllowedToUnclaim(ticket: TicketModel): Boolean {
            if (hasPermission(Permission.Administrator) || hasPermission(Permission.ManageChannels)) return true

            return ticket.claimer?.id == id.value.toLong()
        }
    }
}