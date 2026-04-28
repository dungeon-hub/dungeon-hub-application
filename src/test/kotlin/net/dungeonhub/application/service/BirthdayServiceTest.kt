package net.dungeonhub.application.service

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BirthdayServiceTest {

    @Test
    fun testDateParsing() {
        assertEquals(LocalDate(2024, 1, 1), BirthdayService.parseBirthdayDate("20240101"))

        assertNull(BirthdayService.parseBirthdayDate("20240132"))

        assertNull(BirthdayService.parseBirthdayDate("2024121"))

        assertEquals(LocalDate(2024, 12, 31), BirthdayService.parseBirthdayDate("20241231"))

        assertNull(BirthdayService.parseBirthdayDate("20241231abs"))
    }

    @Test
    fun testTimeZoneParsing() {
        val tzBerlin = BirthdayService.parseTimeZone("Europe/Berlin")
        assertEquals(true, tzBerlin != null)

        val tzOffset = BirthdayService.parseTimeZone("+0530")
        assertEquals(true, tzOffset != null)

        val tzInvalid = BirthdayService.parseTimeZone("invalid/timezone")
        assertNull(tzInvalid)
    }

    @Test
    fun testBirthdayWithTimezone() {
        val birthdayWithTz = BirthdayService.Birthday(
            eventName = "Alice | Birthday",
            date = LocalDate(2024, 1, 2),
            userId = 123456789L,
            birthYear = 30,
            timezone = "Europe/Berlin"
        )

        assertEquals("Alice", birthdayWithTz.username)
        assertEquals(123456789L, birthdayWithTz.userId)
        assertEquals(30, birthdayWithTz.birthYear)
        assertEquals("Europe/Berlin", birthdayWithTz.timezone)

        val birthdayWithoutTz = BirthdayService.Birthday(
            eventName = "Bob | Birthday",
            date = LocalDate(2024, 1, 2),
            userId = 987654321L,
            birthYear = 25
        )

        assertNull(birthdayWithoutTz.timezone)
    }
}
