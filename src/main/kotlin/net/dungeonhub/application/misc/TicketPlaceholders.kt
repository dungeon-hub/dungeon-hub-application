package net.dungeonhub.application.misc

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.exceptions.NotLinkedException
import net.dungeonhub.application.listener.ticket.TicketFormListener
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection

// TODO implement more placeholders
class TicketPlaceholders(
    val ticketPanel: TicketPanelModel,
    val ticket: TicketModel,
    val interactionUser: Member,
    val ticketChannel: TextChannel?,
    val transcriptUrl: String? = null,
    val cacheExpiration: Int = 60 * 3
) {
    val ticketUserId = ticket.user.id
    val ticketUserIgn by lazy {
        ticketUserModel?.minecraftId?.let { MojangConnection.getNameByUUID(it) }
    }

    val ticketUser by lazy {
        runBlocking {
            DiscordConnection.bot.kordRef
                .getUser(Snowflake(ticketUserId))
                ?.asMemberOrNull(Snowflake(ticketPanel.discordServer.id))
        }
    }

    val ticketUserModel by lazy {
        DiscordUserConnection.authenticated().getByIdOrCreate(ticketUserId)
    }

    val carryTier by lazy {
        // TODO dedicated endpoint
        DiscordServerConnection.authenticated().getAllCarryTiers(ticketPanel.discordServer.id)
            ?.firstOrNull { carryTier ->
                carryTier.relatedTicketPanel?.id == ticket.ticketPanel.id
            }
    }

    val formCarryDifficultyName by lazy { ticket.formResponses.firstOrNull { it.customId == "carry-difficulty" }?.value }
    val formCarryDifficulty by lazy {
        formCarryDifficultyName?.let { formCarryDifficultyName ->
            carryTier?.let { carryTier ->
                TicketFormListener.findCarryDifficulty(carryTier, formCarryDifficultyName)
            }
        }
    }

    val formCarryAmount by lazy { ticket.formResponses.firstOrNull { it.customId == "carry-amount" }?.value }

    val replacements: Map<String, () -> String>
        get() {
            val apiConnection = HypixelApiConnection().withCacheExpiration(cacheExpiration)

            val replacements: MutableMap<String, () -> String> = HashMap()

            replacements["user.mention"] = { "<@${ticketUserId}>" }
            replacements["user.displayName"] = { ticketUser?.effectiveName ?: "unknown" }
            replacements["interactionUser.displayName"] = { interactionUser.effectiveName }
            replacements["user.minecraft.name"] = { ticketUserIgn ?: "unlinked" }
            replacements["user.skyblock.level"] = {
                apiConnection.getSkyblockProfiles(
                    ticketUserModel?.minecraftId ?: throw NotLinkedException()
                )?.profiles?.maxOfOrNull {
                    it.getCurrentMember(ticketUserModel?.minecraftId ?: throw NotLinkedException())?.leveling?.level
                        ?: 0
                }?.toString() ?: "?"
            }
            replacements["user.catacombs.level"] = {
                apiConnection.getSkyblockProfiles(
                    ticketUserModel?.minecraftId ?: throw NotLinkedException()
                )?.profiles?.maxOfOrNull {
                    it.getCurrentMember(
                        ticketUserModel?.minecraftId ?: throw NotLinkedException()
                    )?.dungeons?.catacombsLevel
                        ?: 0
                }?.toString() ?: "?"
            }
            replacements["panel.name"] = { ticketPanel.displayName ?: ticketPanel.name }
            replacements["ticket.id"] = { ticket.id.toString() }
            replacements["ticket.name"] = { ticketChannel?.name ?: "not-set" }
            replacements["transcript.url"] = { transcriptUrl ?: "unknown" }
            replacements["carry-tier.name"] = { carryTier?.displayName ?: "unknown" }
            replacements["carry-difficulty.name"] = { formCarryDifficulty?.displayName ?: "unknown" }
            replacements["ticket.form.1"] = { ticket.formResponses.firstOrNull { it.ordinal == 1 }?.value ?: "unknown" }
            replacements["ticket.form.2"] = { ticket.formResponses.firstOrNull { it.ordinal == 2 }?.value ?: "unknown" }
            replacements["ticket.form.3"] = { ticket.formResponses.firstOrNull { it.ordinal == 3 }?.value ?: "unknown" }
            replacements["ticket.form.4"] = { ticket.formResponses.firstOrNull { it.ordinal == 4 }?.value ?: "unknown" }
            replacements["ticket.form.5"] = { ticket.formResponses.firstOrNull { it.ordinal == 5 }?.value ?: "unknown" }
            replacements["ticket.form.carry-difficulty"] = { formCarryDifficulty?.displayName ?: "unknown" }
            replacements["ticket.form.carry-amount"] = { formCarryAmount ?: "unknown" }

            return replacements
        }
}