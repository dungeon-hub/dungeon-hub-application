package net.dungeonhub.application.listener.ticket

import net.dungeonhub.enums.TranscriptTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TranscriptRequestTest {
    private fun resolve(
        channelRequested: Boolean = false,
        isStaff: Boolean = true,
        hasTranscriptChannel: Boolean = true,
        sendToUser: Boolean = true,
    ) = resolveTranscriptDestination(channelRequested, isStaff, hasTranscriptChannel, sendToUser)

    @Test
    fun `default sends to configured channel and user`() {
        assertEquals(TranscriptDestination.Send(TranscriptTarget.Both), resolve())
    }

    @Test
    fun `default without transcript channel only sends to user`() {
        assertEquals(TranscriptDestination.Send(TranscriptTarget.User), resolve(hasTranscriptChannel = false))
    }

    @Test
    fun `disabling dm only sends to channel`() {
        assertEquals(TranscriptDestination.Send(TranscriptTarget.TranscriptChannel), resolve(sendToUser = false))
    }

    @Test
    fun `disabling dm without transcript channel is rejected instead of sending a dm`() {
        assertEquals(TranscriptDestination.NoDestination, resolve(hasTranscriptChannel = false, sendToUser = false))
    }

    @Test
    fun `staff can pick a channel`() {
        assertEquals(TranscriptDestination.Send(TranscriptTarget.Both), resolve(channelRequested = true))
        assertEquals(
            TranscriptDestination.Send(TranscriptTarget.TranscriptChannel),
            resolve(channelRequested = true, sendToUser = false),
        )
    }

    @Test
    fun `non-staff can't pick a channel`() {
        assertEquals(TranscriptDestination.ChannelNotAllowed, resolve(channelRequested = true, isStaff = false))
        assertEquals(
            TranscriptDestination.ChannelNotAllowed,
            resolve(channelRequested = true, isStaff = false, sendToUser = false),
        )
    }

    @Test
    fun `non-staff can use the configured channel`() {
        assertEquals(TranscriptDestination.Send(TranscriptTarget.Both), resolve(isStaff = false))
    }

    @Test
    fun `cooldown blocks repeated requests for the same user and ticket`() {
        val timeSource = TestTimeSource()
        val cooldown = TranscriptCooldown(60.seconds, timeSource)

        assertNull(cooldown.tryAcquire(userId = 1, ticketId = 10))

        timeSource += 20.seconds
        assertEquals(40.seconds, cooldown.tryAcquire(userId = 1, ticketId = 10))
    }

    @Test
    fun `cooldown is separate per user and per ticket`() {
        val cooldown = TranscriptCooldown(60.seconds, TestTimeSource())

        assertNull(cooldown.tryAcquire(userId = 1, ticketId = 10))
        assertNull(cooldown.tryAcquire(userId = 2, ticketId = 10))
        assertNull(cooldown.tryAcquire(userId = 1, ticketId = 11))
        assertNotNull(cooldown.tryAcquire(userId = 1, ticketId = 10))
    }

    @Test
    fun `cooldown expires`() {
        val timeSource = TestTimeSource()
        val cooldown = TranscriptCooldown(60.seconds, timeSource)

        assertNull(cooldown.tryAcquire(userId = 1, ticketId = 10))

        timeSource += 60.seconds
        assertNull(cooldown.tryAcquire(userId = 1, ticketId = 10))
    }
}
