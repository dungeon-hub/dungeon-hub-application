package net.dungeonhub.application.listener.ticket

import net.dungeonhub.enums.TicketState

internal enum class TicketClaimAction {
    Claim,
    Unclaim,
    NotATicket,
    NotClaimable,
    Deleted,
}

internal fun resolveTicketClaimAction(
    ticketExists: Boolean,
    claimable: Boolean,
    state: TicketState?,
    hasClaimer: Boolean,
): TicketClaimAction = when {
    !ticketExists -> TicketClaimAction.NotATicket
    !claimable -> TicketClaimAction.NotClaimable
    state == TicketState.Deleted -> TicketClaimAction.Deleted
    hasClaimer -> TicketClaimAction.Unclaim
    else -> TicketClaimAction.Claim
}
