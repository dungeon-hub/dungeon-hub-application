package net.dungeonhub.application.event

import dev.kordex.core.events.KordExEvent
import net.dungeonhub.model.ticket.TicketModel

class TicketTranscriptCreatedEvent(
    val ticket: TicketModel,
    val transcriptUrl: String
) : KordExEvent
