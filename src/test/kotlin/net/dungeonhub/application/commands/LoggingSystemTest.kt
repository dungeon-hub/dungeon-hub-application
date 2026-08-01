package net.dungeonhub.application.commands

import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.enums.IngameCarryType
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.carry_tier.CarryTierModel
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.discord_server.DiscordServerModel
import net.dungeonhub.model.discord_user.DiscordUserModel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.toKotlinInstant

class LoggingSystemTest {
    @Test
    fun `repeated single carries from the same carrier and difficulty are merged`() {
        val first = queue(id = 1, playerId = 101, time = Instant.parse("2026-01-01T10:00:00Z"))
        val second = queue(id = 2, playerId = 102, time = Instant.parse("2026-01-01T10:01:00Z"))
        val third = queue(id = 3, playerId = 103, time = Instant.parse("2026-01-01T10:02:00Z"))

        val groups = LoggingSystem.compactCarryEntries(listOf(first, second, third))

        assertEquals(1, groups.size)
        assertEquals(listOf(first, second, third), groups.single())
    }

    @Test
    fun `entries are ordered by time before adjacent carries are merged`() {
        val first = queue(id = 1, time = Instant.parse("2026-01-01T10:00:00Z"))
        val second = queue(id = 2, time = Instant.parse("2026-01-01T10:01:00Z"))
        val third = queue(id = 3, time = Instant.parse("2026-01-01T10:02:00Z"))

        val groups = LoggingSystem.compactCarryEntries(listOf(third, first, second))

        assertEquals(listOf(first, second, third), groups.single())
    }

    @Test
    fun `single carries with different carriers are not merged`() {
        val first = queue(id = 1, carrierId = 10)
        val second = queue(id = 2, carrierId = 11)

        assertEquals(listOf(listOf(first), listOf(second)), LoggingSystem.compactCarryEntries(listOf(first, second)))
    }

    @Test
    fun `single carries with different difficulties are not merged`() {
        val first = queue(id = 1, difficulty = difficulty(id = 1, displayName = "Floor One"))
        val second = queue(id = 2, difficulty = difficulty(id = 2, displayName = "Floor Two"))

        assertEquals(listOf(listOf(first), listOf(second)), LoggingSystem.compactCarryEntries(listOf(first, second)))
    }

    @Test
    fun `bulk carries remain separate and break a run of mergeable entries`() {
        val first = queue(id = 1)
        val bulk = queue(id = 2, amount = 2)
        val last = queue(id = 3)

        assertEquals(
            listOf(listOf(first), listOf(bulk), listOf(last)),
            LoggingSystem.compactCarryEntries(listOf(first, bulk, last))
        )
    }

    @Test
    fun `an empty carry collection produces no groups`() {
        assertEquals(emptyList(), LoggingSystem.compactCarryEntries(emptyList()))
    }

    @Test
    fun `grouped carry message contains totals players score and transcript`() {
        val first = queue(
            id = 1,
            playerId = 101,
            attachmentLink = "https://example.test/transcript",
            time = Instant.parse("2026-01-01T10:00:00Z")
        )
        val second = queue(
            id = 2,
            playerId = 102,
            time = Instant.parse("2026-01-01T10:01:00Z")
        )

        val embed = LoggingSystem.loadGroupedCarryEmbed(listOf(first, second))

        assertEquals(Instant.parse("2026-01-01T10:01:00Z").toKotlinInstant(), embed.timestamp)
        assertEquals(EmbedColor.Information.color, embed.color)
        assertEquals("2", embed.fields.single { it.name == "Number of carries" }.value)
        assertEquals("Dungeon - Floor One", embed.fields.single { it.name == "Type of carry" }.value)
        assertEquals("<@101>\n<@102>", embed.fields.single { it.name == "Players" }.value)
        assertEquals("<@10>", embed.fields.single { it.name == "Carrier" }.value)
        assertEquals("10", embed.fields.single { it.name == "Gained score" }.value)
        assertEquals(
            "[Click to open](https://example.test/transcript)",
            embed.fields.single { it.name == "Transcript-Link" }.value
        )
    }

    @Test
    fun `grouped carry message uses singular player label and omits absent transcript`() {
        val carry = queue(id = 1, playerId = 101, amount = 3, attachmentLink = null)

        val embed = LoggingSystem.loadGroupedCarryEmbed(listOf(carry))

        assertEquals("<@101>", embed.fields.single { it.name == "Player" }.value)
        assertEquals("3", embed.fields.single { it.name == "Number of carries" }.value)
        assertEquals("15", embed.fields.single { it.name == "Gained score" }.value)
        assertNull(embed.fields.singleOrNull { it.name == "Players" })
        assertNull(embed.fields.singleOrNull { it.name == "Transcript-Link" })
    }

    @Test
    fun `compaction retains the original queue model instances`() {
        val carry = queue(id = 1)

        assertSame(carry, LoggingSystem.compactCarryEntries(listOf(carry)).single().single())
    }

    private fun queue(
        id: Long,
        carrierId: Long = 10,
        playerId: Long = 100,
        amount: Int = 1,
        difficulty: CarryDifficultyModel = difficulty(),
        attachmentLink: String? = null,
        time: Instant = Instant.parse("2026-01-01T10:00:00Z")
    ) = CarryQueueModel(
        id,
        QueueStep.Transcript,
        DiscordUserModel(carrierId, null, null),
        DiscordUserModel(playerId, null, null),
        amount,
        difficulty,
        null,
        attachmentLink,
        time
    )

    private fun difficulty(
        id: Long = 1,
        displayName: String = "Floor One",
        score: Int = 5
    ): CarryDifficultyModel {
        val carryType = CarryTypeModel(1, "dungeon", "Dungeons", DiscordServerModel(1), null, false)
        val tier = CarryTierModel(1, "dungeon", "Dungeon", carryType, null, null, null, null, null)
        return CarryDifficultyModel(
            id,
            "floor-$id",
            displayName,
            tier,
            1,
            null,
            null,
            score,
            null,
            null,
            IngameCarryType.Floor1
        )
    }
}
