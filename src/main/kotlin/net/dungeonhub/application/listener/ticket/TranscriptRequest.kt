package net.dungeonhub.application.listener.ticket

import net.dungeonhub.enums.TranscriptTarget
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

internal sealed interface TranscriptDestination {
    data class Send(val target: TranscriptTarget) : TranscriptDestination
    data object ChannelNotAllowed : TranscriptDestination
    data object NoDestination : TranscriptDestination
}

/**
 * Decides where a manually requested transcript goes.
 * Only staff may pick a channel; without a transcript channel and without a DM there is nowhere to send it.
 */
internal fun resolveTranscriptDestination(
    channelRequested: Boolean,
    isStaff: Boolean,
    hasTranscriptChannel: Boolean,
    sendToUser: Boolean,
): TranscriptDestination = when {
    channelRequested && !isStaff -> TranscriptDestination.ChannelNotAllowed
    !hasTranscriptChannel && !sendToUser -> TranscriptDestination.NoDestination
    !hasTranscriptChannel -> TranscriptDestination.Send(TranscriptTarget.User)
    sendToUser -> TranscriptDestination.Send(TranscriptTarget.Both)
    else -> TranscriptDestination.Send(TranscriptTarget.TranscriptChannel)
}

/**
 * Limits manual transcript requests per user per ticket.
 */
internal class TranscriptCooldown(
    private val duration: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private val lastRequests = ConcurrentHashMap<Pair<Long, Long>, ComparableTimeMark>()

    /**
     * Records a request and returns null, or returns the remaining cooldown if the user has to wait.
     */
    fun tryAcquire(userId: Long, ticketId: Long): Duration? {
        lastRequests.entries.removeIf { it.value.elapsedNow() >= duration }

        var remaining: Duration? = null

        lastRequests.compute(userId to ticketId) { _, lastRequest ->
            if(lastRequest == null) {
                timeSource.markNow()
            } else {
                remaining = duration - lastRequest.elapsedNow()
                lastRequest
            }
        }

        return remaining
    }
}
