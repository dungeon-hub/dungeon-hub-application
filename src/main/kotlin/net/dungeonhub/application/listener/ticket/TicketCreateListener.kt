package net.dungeonhub.application.listener.ticket

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.ActionInteractionCreateEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.ModalBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import kotlinx.coroutines.launch
import net.dungeonhub.application.commands.CalcPriceCommand
import net.dungeonhub.application.commands.TicketSystem
import net.dungeonhub.application.commands.TicketSystem.Companion.getCategory
import net.dungeonhub.application.commands.TicketSystem.Companion.replacePlaceholders
import net.dungeonhub.application.commands.TicketSystem.Companion.scheduler
import net.dungeonhub.application.commands.TicketSystem.Companion.updateTicketPermissions
import net.dungeonhub.application.commands.addSilentLinkButtons
import net.dungeonhub.application.connection.applyJson
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.TicketPlaceholders
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.MessagesService
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.*
import net.dungeonhub.enums.FormType
import net.dungeonhub.enums.TicketState
import net.dungeonhub.hypixel.entities.skyblock.statsoverview.BuiltInStatsOverviewType
import net.dungeonhub.hypixel.entities.skyblock.statsoverview.StatsOverviewType
import net.dungeonhub.model.ticket.TicketCreationModel
import net.dungeonhub.model.ticket.TicketFormResponseModel
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelFormModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection
import net.dungeonhub.service.GsonService

@LoadExtension
class TicketCreateListener : Extension() {
    override val name = "ticket-create-listener"

    override suspend fun setup() {
        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.componentId.startsWith("create-ticket-"))
            }

            action {
                val panelId = event.interaction.componentId.removePrefix("create-ticket-")

                val ticketPanel = panelId.toLongOrNull()?.let {
                    TicketPanelConnection[event.interaction.guildId.value.toLong()].authenticated()
                        .getById(it)
                }

                if(!checkTicketOpen(event, ticketPanel, panelId)) return@action

                if(ticketPanel!!.formQuestions.isNotEmpty()) {
                    event.interaction.modal(
                        ticketPanel.displayName ?: ticketPanel.name,
                        "ticket-form-${ticketPanel.id}"
                    ) {
                        ticketPanel.formQuestions.forEach { formQuestion ->
                            loadModalOption(event.interaction.user, ticketPanel, formQuestion)
                        }
                    }
                    return@action
                }

                doTicketOpen(event, ticketPanel, emptyList())
            }
        }
    }

    suspend fun ModalBuilder.loadModalOption(
        member: Member,
        ticketPanel: TicketPanelModel,
        formQuestion: TicketPanelFormModel
    ) {
        val data = JsonParser.parseString(formQuestion.data)?.asJsonPrimitive?.asString

        if(formQuestion.type == FormType.Predefined) {
            if(data == "ign-display") { // TODO enum?
                val discordUser = DiscordUserConnection.authenticated().getLinkedById(member.id.value.toLong()) ?: return

                val ign = discordUser.minecraftId?.let { MojangConnection.getNameByUUID(it) } ?: return

                textDisplay {
                    content = "You're currently linked to the Minecraft account `$ign`.\n" +
                            "-# If that seems incorrect, please unlink using `/unlink` and then `/link` to the correct account."
                }
            } else if(data == "carry-difficulty") { // TODO enum?
                // TODO dedicated endpoint
                val carryTier = DiscordServerConnection.authenticated()
                    .getAllCarryTiers(ticketPanel.discordServer.id)
                    ?.firstOrNull { carryTier ->
                        carryTier.relatedTicketPanel?.id == ticketPanel.id
                    } ?: return

                val carryDifficulties = CarryDifficultyConnection[carryTier].authenticated().getAllCarryDifficulties()
                    ?: return

                label("Carry Difficulty") {
                    stringSelect("carry-difficulty") {
                        carryDifficulties.forEach {
                            option(
                                it.displayName,
                                it.identifier
                            )
                        }
                    }
                }
            } else if(data == "carry-amount") { // TODO enum?
                actionRow {
                    textInput( // TODO make this a select menu once that becomes available in Kord
                        TextInputStyle.Short,
                        "carry-amount",
                        "Enter a number of carries"
                    ) {
                        placeholder = "Only enter a number here"
                        required = true
                    }
                }
            }
        } else {
            // TODO build discord form question
        }
    }

    companion object {
        const val DEFAULT_CONTENT = "Welcome, {user.mention}!\nPlease describe your {panel.name} request below further."

        suspend fun checkTicketOpen(
            event: ActionInteractionCreateEvent,
            ticketPanel: TicketPanelModel?,
            panelId: String
        ): Boolean {
            if (ticketPanel == null) {
                event.interaction.deferEphemeralResponse().respond {
                    addEmbed {
                        description = "Couldn't load ticket panel #$panelId."
                        color(EmbedColor.Negative)
                    }
                }
                return false
            }

            if (ticketPanel.requiresLinking && DiscordUserConnection.authenticated()
                    .getLinkedById(event.interaction.user.id.value.toLong()) == null
            ) {
                event.interaction.deferEphemeralResponse().respond {
                    addEmbed {
                        description =
                            "You're currently not linked, which this ticket panel requires.\nPlease link to your minecraft account using the buttons below.\nAfterwards, try opening a ticket again."
                        color(EmbedColor.Negative)
                    }
                    actionRow {
                        addSilentLinkButtons()
                    }
                }
                return false
            }

            return true
        }

        suspend fun doTicketOpen(
            event: ActionInteractionCreateEvent,
            ticketPanel: TicketPanelModel,
            responses: List<TicketFormResponseModel>
        ) {
            val response = event.interaction.deferEphemeralResponse()

            response.respond {
                // TODO check for ticket limit

                val member = event.interaction.user.asMember(Snowflake(ticketPanel.discordServer.id))

                val ticket = createTicketModel(ticketPanel, member, responses)

                if (ticket == null) {
                    addEmbed {
                        description = "Couldn't create the ticket in the API."
                        color(EmbedColor.Negative)
                    }
                    return@respond
                }

                val ticketChannel = createTicketChannel(ticketPanel, ticket, member)
                updateTicketChannel(ticket, ticketChannel)

                addEmbed {
                    description = "Ticket created: ${ticketChannel.mention}."
                    color(EmbedColor.Positive)
                }

                scheduler.launch {
                    sendInitialTicketMessage(ticketPanel, ticket, ticketChannel, member)
                }
            }
        }

        suspend fun createTicketModel(panel: TicketPanelModel, user: MemberBehavior, responses: List<TicketFormResponseModel>): TicketModel? {
            val connection = TicketConnection[user.guildId.value.toLong(), panel].authenticated()

            val creationModel = TicketCreationModel(
                TicketState.Creating,
                null,
                user.id.value.toLong(),
                null,
                responses
            )

            return connection.addNewTicket(creationModel)
        }

        suspend fun createTicketChannel(ticketPanel: TicketPanelModel, ticket: TicketModel, member: Member): TextChannel {
            val name = TicketSystem.buildTicketName(ticketPanel, ticket, member, null)

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

        suspend fun updateTicketChannel(ticket: TicketModel, ticketChannel: TextChannel): TicketModel? {
            val connection = TicketConnection[ticketChannel.guildId.value.toLong(), ticket.ticketPanel].authenticated()

            val updateModel = ticket.getUpdateModel()
            updateModel.channel = ticketChannel.id.value.toLong()
            updateModel.state = TicketState.Open

            return connection.updateTicket(ticket.id, updateModel)
        }

        suspend fun sendInitialTicketMessage(
            ticketPanel: TicketPanelModel,
            ticket: TicketModel,
            ticketChannel: TextChannel,
            member: Member
        ) {
            var content: String
            var embeds = mutableListOf<EmbedBuilder>()
            var additionalButtons: List<(suspend ActionRowBuilder.() -> Unit)?>

            @Suppress("DEPRECATION")
            val messageJson = try {
                ticketPanel.ticketMessage?.let {
                    GsonService.gson.fromJson(it, JsonObject::class.java)
                }
            } catch (_: JsonSyntaxException) {
                null
            }

            val placeholders = TicketPlaceholders(ticketPanel, ticket, member, ticketChannel)

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
        ): (suspend ActionRowBuilder.() -> Unit)? {
            return when (additionalButton) {
                "user.skycrypt" -> {
                    {
                        placeholders.ticketUserIgn.await()?.let {
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

            suspend fun parseJsonObjectEmbed(jsonElement: JsonElement): EmbedBuilder? {
                val embedBuilder = EmbedBuilder()

                val jsonObject = jsonElement.asJsonObject

                if(jsonObject.has("customEmbed") && !jsonObject.getAsJsonPrimitive("customEmbed")?.asString.isNullOrEmpty()) {
                    return buildCustomEmbed(
                        jsonObject.getAsJsonPrimitive("customEmbed")?.asString!!,
                        placeholders,
                        jsonObject.getAsJsonPrimitive("customData")?.asString
                    )
                }

                jsonObject
                    .entrySet()
                    .forEach { entry: Map.Entry<String, JsonElement> ->
                        embedBuilder.applyJson(
                            entry.key,
                            replacePlaceholders(entry.value, placeholders)
                        )
                    }

                return embedBuilder
            }

            try {
                if (embedData.isJsonObject) {
                    parseJsonObjectEmbed(embedData)?.let { embedBuilders.add(it) }
                } else if (embedData.isJsonArray) {
                    embedData.asJsonArray
                        .forEach { jsonElement: JsonElement ->
                            if (jsonElement.isJsonObject) {
                                parseJsonObjectEmbed(jsonElement)?.let { embedBuilders.add(it) }
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

        suspend fun buildCustomEmbed(type: String, placeholders: TicketPlaceholders, customData: String? = null): EmbedBuilder? {
            return when (type) {
                "stats-overview" -> placeholders.ticketUserIgn.await()?.let {
                    val customStats: List<StatsOverviewType>? = customData?.split(",")
                        ?.mapNotNull { statsType -> try { BuiltInStatsOverviewType.valueOf(statsType) } catch (_: IllegalArgumentException) { null } }

                    ApplicationService.getPlayerDataEmbed(
                        it,
                        placeholders.ticketUserId,
                        statsOverviewTypes = customStats
                    )
                }
                "price-overview" -> placeholders.carryTier.await()?.let { MessagesService.getPriceEmbed(it) }
                "carry-price" -> placeholders.formCarryDifficulty.await()?.let { carryDifficulty ->
                    placeholders.formCarryAmount?.toIntOrNull()?.let { carryAmount ->
                        CalcPriceCommand.generateCalculatedPriceEmbed(carryDifficulty, carryAmount)
                    }
                }

                else -> null
            }
        }

        fun getDefaultButtons(claimButton: Boolean): List<suspend ActionRowBuilder.() -> Unit> {
            return listOf<suspend ActionRowBuilder.() -> Unit>({
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
            additionalButtons: List<suspend ActionRowBuilder.() -> Unit>
        ) {
            val allButtons: List<suspend ActionRowBuilder.() -> Unit> = getDefaultButtons(ticketPanel.claimable) + additionalButtons

            val message = ticketChannel.createMessage {
                this.content = content
                this.embeds = embeds.toMutableList()

                allButtons.windowed(5, 5, true).forEach { actionRowBuilders ->
                    actionRow {
                        actionRowBuilders.forEach { actionRowBuilder -> actionRowBuilder() }
                    }
                }
            }

            // TODO config for that?
            message.pin()
        }
    }
}