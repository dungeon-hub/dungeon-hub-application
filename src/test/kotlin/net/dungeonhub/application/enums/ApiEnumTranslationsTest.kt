package net.dungeonhub.application.enums

import net.dungeonhub.application.TestHelper
import net.dungeonhub.enums.RoleAction
import net.dungeonhub.enums.RoleRequirementComparison
import net.dungeonhub.enums.RoleRequirementType
import net.dungeonhub.enums.ScoreResetType
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.enums.StaticMessageType
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.discord_server.DiscordServerModel
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiEnumTranslationsTest {
    @BeforeTest
    fun setUp() {
        TestHelper.buildKoinContext()
    }

    @Test
    fun testLeaderboardTitle() {
        for (scoreType in ScoreType.entries) {
            val locale = Locale.ENGLISH

            val suffix = if (scoreType.leaderboardSuffix?.withLocale(locale)?.translate().isNullOrBlank())
                ""
            else
                " ${scoreType.leaderboardSuffix!!.withLocale(locale).translate()}"

            val carryType = CarryTypeModel(0, "identifier", "DisplayName", DiscordServerModel(0), null, false)

            assertEquals(
                "Leaderboard | Total score$suffix",
                scoreType.getLeaderboardTitle(null)
            )

            assertEquals(
                "Leaderboard | DisplayName score$suffix",
                scoreType.getLeaderboardTitle(carryType)
            )
        }
    }

    @Test
    fun testScoreTypeKeys() {
        val keys = mapOf(
            ScoreType.Default to "score-type.default.readable-name",
            ScoreType.Alltime to "score-type.alltime.readable-name",
            ScoreType.Event to "score-type.event.readable-name"
        )

        val translations = mapOf(
            ScoreType.Default to "Current",
            ScoreType.Alltime to "All time",
            ScoreType.Event to "Event"
        )

        for (scoreType in ScoreType.entries) {
            assertEquals(keys[scoreType], scoreType.readableName.key)
            assertEquals(translations[scoreType], scoreType.readableName.translate())
        }
    }

    @Test
    fun testRoleActionTranslations() {
        assertEquals("role-actions.apply-when-verified.readable-name", RoleAction.ApplyWhenVerified.readableName.key)
        assertEquals("Apply when verified", RoleAction.ApplyWhenVerified.readableName.translate())
        assertEquals("Vergeben, wenn verifiziert", RoleAction.ApplyWhenVerified.readableName.withLocale(Locale.GERMAN).translate())
    }

    @Test
    fun testUntranslatedReadableNames() {
        assertEquals("Skyblock Level", RoleRequirementType.SkyblockLevel.readableName.key)
        assertEquals("Score Leaderboard", StaticMessageType.ScoreLeaderboard.readableName.key)
        assertEquals("Both", ScoreResetType.Both.readableName.key)
        assertEquals(">=", RoleRequirementComparison.GreaterOrEqual.readableName.key)
    }
}
