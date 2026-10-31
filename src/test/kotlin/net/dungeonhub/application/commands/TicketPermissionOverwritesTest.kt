package net.dungeonhub.application.commands

import dev.kord.common.entity.Overwrite
import dev.kord.common.entity.OverwriteType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.channel.TextChannelCreateBuilder
import net.dungeonhub.application.commands.TicketSystem.Companion.toKordPermission
import net.dungeonhub.application.commands.TicketSystem.Companion.updateTicketPermissions
import net.dungeonhub.enums.DiscordChannelPermission
import net.dungeonhub.enums.RoleAction
import net.dungeonhub.enums.TicketPermissionCandidate
import net.dungeonhub.enums.TicketState
import net.dungeonhub.enums.TranscriptTarget
import net.dungeonhub.model.discord_role.DiscordRoleModel
import net.dungeonhub.model.discord_server.DiscordServerModel
import net.dungeonhub.model.discord_user.DiscordUserModel
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketCandidatePermissionsModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.model.ticket_panel.TicketPanelPermissionsModel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TicketPermissionOverwritesTest {
    private val server = DiscordServerModel(1)
    private val supportRole = DiscordRoleModel(10, null, RoleAction.None, server)
    private val additionalRole = DiscordRoleModel(20, null, RoleAction.None, server)
    private val creator = DiscordUserModel(100, null, null)
    private val claimer = DiscordUserModel(200, null, null)

    private val standard = Permissions(listOf(Permission.ViewChannel, Permission.SendMessages, Permission.ReadMessageHistory))
    private val hidden = Permissions(listOf(Permission.ViewChannel))

    @Test
    fun testOpenTicket() {
        val overwrites = overwrites(TicketState.Open, null)

        assertEquals(setOf(10L, 20L, 100L, 1L), overwrites.keys)
        assertOverwrite(overwrites.getValue(10), OverwriteType.Role, standard)
        assertOverwrite(overwrites.getValue(20), OverwriteType.Role, standard)
        assertOverwrite(overwrites.getValue(100), OverwriteType.Member, standard)
        assertOverwrite(overwrites.getValue(1), OverwriteType.Role, Permissions(), hidden)
    }

    @Test
    fun testClaimedTicketHidesSupportTeam() {
        val overwrites = overwrites(TicketState.Open, claimer)

        assertFalse(10L in overwrites)
        assertOverwrite(overwrites.getValue(20), OverwriteType.Role, standard)
        assertOverwrite(overwrites.getValue(100), OverwriteType.Member, standard)
        assertOverwrite(overwrites.getValue(200), OverwriteType.Member, standard)
        assertOverwrite(overwrites.getValue(1), OverwriteType.Role, Permissions(), hidden)
    }

    @Test
    fun testClosedTicket() {
        val unclaimed = overwrites(TicketState.Closed, null)

        assertFalse(100L in unclaimed)
        assertOverwrite(unclaimed.getValue(10), OverwriteType.Role, standard)
        assertOverwrite(unclaimed.getValue(1), OverwriteType.Role, Permissions(), hidden)

        val claimed = overwrites(TicketState.Closed, claimer)

        assertFalse(10L in claimed)
        assertFalse(100L in claimed)
        assertOverwrite(claimed.getValue(200), OverwriteType.Member, standard)
        assertOverwrite(claimed.getValue(1), OverwriteType.Role, Permissions(), hidden)
    }

    @Test
    fun testClosedTicketUsesTheConfiguredCreatorPermissions() {
        val permissions = TicketPanelPermissionsModel(
            closed = mapOf(
                TicketPermissionCandidate.TicketCreator to TicketCandidatePermissionsModel(
                    channel = mapOf(
                        DiscordChannelPermission.ViewChannel to true,
                        DiscordChannelPermission.SendMessages to false
                    )
                )
            )
        ).resolved()

        val overwrites = overwrites(TicketState.Closed, null, permissions)

        assertOverwrite(
            overwrites.getValue(100),
            OverwriteType.Member,
            Permissions(listOf(Permission.ViewChannel)),
            Permissions(listOf(Permission.SendMessages))
        )
    }

    @Test
    fun testCreatorAndClaimerAreMerged() {
        val permissions = TicketPanelPermissionsModel(
            open = mapOf(
                TicketPermissionCandidate.TicketCreator to TicketCandidatePermissionsModel(
                    channel = mapOf(
                        DiscordChannelPermission.ViewChannel to true,
                        DiscordChannelPermission.SendMessages to false,
                        DiscordChannelPermission.AttachFiles to false
                    )
                ),
                TicketPermissionCandidate.TicketClaimer to TicketCandidatePermissionsModel(
                    channel = mapOf(DiscordChannelPermission.SendMessages to true)
                )
            )
        ).resolved()

        val overwrites = overwrites(TicketState.Open, creator, permissions)

        assertOverwrite(
            overwrites.getValue(100),
            OverwriteType.Member,
            Permissions(listOf(Permission.ViewChannel, Permission.SendMessages)),
            Permissions(listOf(Permission.AttachFiles))
        )
    }

    @Test
    fun testEmptyChannelPermissionsAddNoOverwrite() {
        val permissions = TicketPanelPermissionsModel(
            open = mapOf(TicketPermissionCandidate.Everyone to TicketCandidatePermissionsModel())
        ).resolved()

        assertFalse(1L in overwrites(TicketState.Open, null, permissions))
    }

    @Test
    fun testKordPermissionsMatchTheDiscordBits() {
        for (permission in DiscordChannelPermission.entries) {
            assertEquals(permission.bit.toString(), permission.toKordPermission().code.value)
        }

        assertTrue(DiscordChannelPermission.entries.map { it.toKordPermission() }.toSet().size == DiscordChannelPermission.entries.size)
    }

    private fun assertOverwrite(overwrite: Overwrite, type: OverwriteType, allowed: Permissions, denied: Permissions = Permissions()) {
        assertEquals(type, overwrite.type)
        assertEquals(allowed, overwrite.allow)
        assertEquals(denied, overwrite.deny)
    }

    private fun overwrites(
        state: TicketState,
        claimer: DiscordUserModel?,
        permissions: TicketPanelPermissionsModel = TicketPanelPermissionsModel.DEFAULT
    ): Map<Long, Overwrite> {
        val ticketPanel = ticketPanel(permissions)
        val builder = TextChannelCreateBuilder("ticket")

        builder.updateTicketPermissions(ticketPanel, ticket(ticketPanel, state, claimer))

        return builder.permissionOverwrites.associateBy { it.id.value.toLong() }
    }

    private fun ticket(ticketPanel: TicketPanelModel, state: TicketState, claimer: DiscordUserModel?): TicketModel {
        return TicketModel(1, state, null, ticketPanel, creator, claimer, Instant.EPOCH, emptyList())
    }

    private fun ticketPanel(permissions: TicketPanelPermissionsModel): TicketPanelModel {
        return TicketPanelModel(
            id = 1,
            name = "support",
            displayName = null,
            emoji = null,
            discordServer = server,
            closeable = true,
            closeConfirmation = false,
            claimable = true,
            openChannelName = null,
            claimedChannelName = null,
            closedChannelName = null,
            transcriptChannel = null,
            ticketMessage = null,
            requiresLinking = false,
            closeTranscriptTarget = TranscriptTarget.User,
            deleteTranscriptTarget = TranscriptTarget.TranscriptChannel,
            userTranscriptDm = null,
            formQuestions = emptyList(),
            relatedCarryTier = null,
            relatedCarryDifficulty = null,
            supportRoles = listOf(supportRole),
            additionalRoles = listOf(additionalRole),
            openCategories = emptyList(),
            closedCategories = emptyList(),
            permissions = permissions
        )
    }
}
