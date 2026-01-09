package net.dungeonhub.application.commands

import com.google.gson.*
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.channel.PermissionOverwriteBuilder
import dev.kord.rest.builder.channel.PermissionOverwritesBuilder
import dev.kord.rest.builder.channel.addMemberOverwrite
import dev.kord.rest.builder.channel.addRoleOverwrite
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.hasPermission
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dungeonhub.application.connection.applyJson
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.TicketPlaceholders
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.connection.TicketConnection
import net.dungeonhub.connection.TicketPanelConnection
import net.dungeonhub.enums.TicketPermissionCandidate
import net.dungeonhub.enums.TicketPermissionType
import net.dungeonhub.enums.TicketState
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.model.ticket.TicketCreationModel
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection
import net.dungeonhub.service.GsonService
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
                failIfNot(event.interaction.componentId.startsWith("create-ticket-"))
            }

            action {
                val response = event.interaction.deferEphemeralResponse()

                response.respond {
                    val panelId = event.interaction.componentId.removePrefix("create-ticket-")

                    val ticketPanel = panelId.toLongOrNull()?.let {
                        TicketPanelConnection[event.interaction.guildId.value.toLong()].authenticated()
                            .getById(it)
                    }

                    if (ticketPanel == null) {
                        addEmbed {
                            description = "Couldn't load ticket panel #$panelId."
                            color(EmbedColor.Negative)
                        }
                        return@respond
                    }

                    if (ticketPanel.requiresLinking && DiscordUserConnection.authenticated()
                            .getLinkedById(event.interaction.user.id.value.toLong()) == null
                    ) {
                        addEmbed {
                            description =
                                "You're currently not linked, which this ticket panel requires.\nPlease link to your minecraft account using the buttons below.\nAfterwards, try opening a ticket again."
                            color(EmbedColor.Negative)
                        }
                        actionRow {
                            addSilentLinkButtons()
                        }
                        return@respond
                    }

                    // TODO check for ticket limit

                    val ticket = createTicketModel(ticketPanel, event.interaction.user)

                    if (ticket == null) {
                        addEmbed {
                            description = "Couldn't create the ticket in the API."
                            color(EmbedColor.Negative)
                        }
                        return@respond
                    }

                    val ticketChannel = createTicketChannel(ticketPanel, ticket, event.interaction.user)
                    updateTicketChannel(ticket, ticketChannel)

                    addEmbed {
                        description = "Ticket created: ${ticketChannel.mention}."
                        color(EmbedColor.Positive)
                    }

                    scheduler.launch {
                        sendInitialTicketMessage(ticketPanel, ticket, ticketChannel)
                    }
                }
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

    fun createTicketModel(panel: TicketPanelModel, user: MemberBehavior): TicketModel? {
        val connection = TicketConnection[user.guildId.value.toLong(), panel].authenticated()

        val creationModel = TicketCreationModel(
            TicketState.Creating,
            null,
            user.id.value.toLong(),
            null
        )

        return connection.addNewTicket(creationModel)
    }

    suspend fun createTicketChannel(ticketPanel: TicketPanelModel, ticket: TicketModel, member: Member): TextChannel {
        val name = buildTicketName(ticketPanel, ticket, member)

        return member.guild.createTextChannel(name ?: ticketPanel.name) {
            permissionOverwrites.clear()
            updateTicketPermissions(ticketPanel, ticket)

            val categories = if (ticket.state in listOf(TicketState.Creating, TicketState.Open)) {
                ticketPanel.openCategories
            } else {
                ticketPanel.closedCategories
            }
            getCategory(categories)?.let { parentId = Snowflake(it) }
        }
    }

    fun updateTicketChannel(ticket: TicketModel, ticketChannel: TextChannel): TicketModel? {
        val connection = TicketConnection[ticketChannel.guildId.value.toLong(), ticket.ticketPanel].authenticated()

        val updateModel = ticket.getUpdateModel()
        updateModel.channel = ticketChannel.id.value.toLong()
        updateModel.state = TicketState.Open

        return connection.updateTicket(ticket.id, updateModel)
    }

    suspend fun sendInitialTicketMessage(
        ticketPanel: TicketPanelModel,
        ticket: TicketModel,
        ticketChannel: TextChannel
    ) {
        var content: String
        var embeds = mutableListOf<EmbedBuilder>()
        var additionalButtons: List<(ActionRowBuilder.() -> Unit)?>

        @Suppress("DEPRECATION")
        val messageJson = try {
            ticketPanel.ticketMessage?.let {
                GsonService.gson.fromJson(it, JsonObject::class.java)
            }
        } catch (_: JsonSyntaxException) {
            null
        }

        val placeholders = TicketPlaceholders(ticketPanel, ticket)

        content = replacePlaceholders(
            messageJson?.get("content")?.asString ?: DEFAULT_CONTENT,
            placeholders
        ) // TODO maybe rethink this at some point - what if the user actually doesn't want any content being sent? --> maybe option in the ticket panel
        messageJson?.get("embeds")?.let { embeds = parseEmbeds(it, placeholders) }
        additionalButtons = messageJson?.get("additional-buttons")?.asJsonArray?.map {
            parseAdditionalButton(
                it.asString,
                placeholders
            )
        } ?: emptyList()

        sendInitialTicketMessage(ticketPanel, ticketChannel, content, embeds, additionalButtons.filterNotNull())
    }

    fun parseAdditionalButton(
        additionalButton: String,
        placeholders: TicketPlaceholders
    ): (ActionRowBuilder.() -> Unit)? {
        return when (additionalButton) {
            "user.skycrypt" -> {
                {
                    placeholders.ticketUserIgn?.let {
                        linkButton("https://sky.shiiyu.moe/stats/$it") {
                            label = "SkyCrypt"
                        }
                    }
                }
            }
            "user.status" -> {
                {
                    interactionButton(ButtonStyle.Secondary, "ticket-user-status") {
                        label = "User Status"
                    }
                }
            }
            "user.guild_status" -> {
                {
                    interactionButton(ButtonStyle.Secondary, "ticket-guild-status") {
                        label = "User Guild"
                    }
                }
            }

            else -> null
        }
    }

    suspend fun parseEmbeds(embedData: JsonElement, placeholders: TicketPlaceholders): MutableList<EmbedBuilder> {
        val embedBuilders: MutableList<EmbedBuilder> = mutableListOf()

        try {
            if (embedData.isJsonObject) {
                val embedBuilder = EmbedBuilder()

                embedData.asJsonObject
                    .entrySet()
                    .forEach {
                        embedBuilder.applyJson(
                            it.key,
                            replacePlaceholders(it.value, placeholders)
                        )
                    }

                embedBuilders.add(embedBuilder)
            } else if (embedData.isJsonArray) {
                embedData.asJsonArray
                    .forEach { jsonElement: JsonElement ->
                        if (jsonElement.isJsonObject) {
                            val embedBuilder = EmbedBuilder()

                            jsonElement.asJsonObject
                                .entrySet()
                                .forEach { entry: Map.Entry<String, JsonElement> ->
                                    embedBuilder.applyJson(
                                        entry.key,
                                        replacePlaceholders(entry.value, placeholders)
                                    )
                                }

                            embedBuilders.add(embedBuilder)
                        } else if (jsonElement.isJsonPrimitive) {
                            buildCustomEmbed(jsonElement.asString, placeholders)?.let { embedBuilders.add(it) }
                        }
                    }
            } else if (embedData.isJsonPrimitive) {
                buildCustomEmbed(embedData.asString, placeholders)?.let { embedBuilders.add(it) }
            }
        } catch (_: JsonSyntaxException) {

        }

        return embedBuilders
    }

    suspend fun buildCustomEmbed(type: String, placeholders: TicketPlaceholders): EmbedBuilder? {
        return when (type) {
            "stats-overview" -> placeholders.ticketUserIgn?.let {
                ApplicationService.getPlayerDataEmbed(
                    it,
                    placeholders.ticketUserId
                )
            }

            else -> null
        }
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

    fun replacePlaceholders(string: String, placeholders: TicketPlaceholders): String {
        val replacements = placeholders.replacements

        val regex = "(\\{[^}]+})"
        val usernameBuilder = StringBuilder()
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(string)

        while (matcher.find()) {
            val argument = matcher.group(1)

            val repString = replacements[argument.substring(1, argument.length - 1)]?.invoke()
            if (repString != null) {
                matcher.appendReplacement(usernameBuilder, Matcher.quoteReplacement(repString))
            }
        }
        matcher.appendTail(usernameBuilder)

        return usernameBuilder.toString().trim()
    }

    fun getDefaultButtons(claimButton: Boolean): List<ActionRowBuilder.() -> Unit> {
        return listOf<ActionRowBuilder.() -> Unit>({
            interactionButton(ButtonStyle.Danger, "close-ticket") {
                label = "Close"
            }
        }, {
            interactionButton(ButtonStyle.Success, "claim-ticket") {
                label = "Claim"
            }
        }).take(if (claimButton) 2 else 1)
    }

    suspend fun sendInitialTicketMessage(
        ticketPanel: TicketPanelModel,
        ticketChannel: TextChannel,
        content: String,
        embeds: List<EmbedBuilder>,
        additionalButtons: List<ActionRowBuilder.() -> Unit>
    ) {
        val allButtons = getDefaultButtons(ticketPanel.claimable) + additionalButtons

        ticketChannel.createMessage {
            this.content = content
            this.embeds = embeds.toMutableList()

            allButtons.windowed(5, 5, true) { actionRowBuilders ->
                actionRow {
                    actionRowBuilders.forEach { actionRowBuilder -> actionRowBuilder() }
                }
            }
        }
    }

    companion object {
        lateinit var scheduler: Scheduler
        const val DEFAULT_CONTENT = "Welcome, {user.mention}!\nPlease describe your {panel.name} request below further."

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

        // TODO support full placeholders?
        fun buildTicketName(ticketPanel: TicketPanelModel, ticket: TicketModel, member: Member): String? {
            var result = if (ticket.state == TicketState.Open && ticket.claimer != null) {
                ticketPanel.claimedChannelName
            } else {
                when (ticket.state) {
                    TicketState.Creating, TicketState.Open -> ticketPanel.openChannelName
                    TicketState.Closed, TicketState.Deleted -> ticketPanel.closedChannelName
                }
            }

            result = result?.replace("{count}", "${ticket.id}")
            result = result?.replace(
                "{user}",
                member.effectiveName
            ) // TODO currently, member is just the user that interacted with the ticket. this can be someone else in case of claiming/unclaiming tho

            return result
        }

        // TODO is this method correct so far?
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