package net.dungeonhub.application.service

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.taubsie.dungeonhub.application.service.BirthdayService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BirthdayServiceTest {
    @Test
    fun testExecutionTimeCalculation() {
        assertEquals(
            0,
            BirthdayService.calculateExecutionTime(LocalTime(9, 0, 0))
        )

        assertEquals(
            60 * 60,
            BirthdayService.calculateExecutionTime(LocalTime(8, 0, 0))
        )
        assertEquals(
            60 * 22 + 38,
            BirthdayService.calculateExecutionTime(LocalTime(8, 37, 22))
        )

        assertEquals(
            60 * 60 * 23,
            BirthdayService.calculateExecutionTime(LocalTime(10, 0, 0))
        )

        assertEquals(
            60 * 60 * 22 + 60 * 46 + 59,
            BirthdayService.calculateExecutionTime(LocalTime(10, 13, 1))
        )
    }

    @Test
    fun testDateParsing() {
        assertEquals(LocalDate(2024, 1, 1), BirthdayService.parseBirthdayDate("20240101"))

        assertNull(BirthdayService.parseBirthdayDate("20240132"))

        assertNull(BirthdayService.parseBirthdayDate("2024121"))

        assertEquals(LocalDate(2024, 12, 31), BirthdayService.parseBirthdayDate("20241231"))

        assertNull(BirthdayService.parseBirthdayDate("20241231abs"))
    }

    @Test
    fun testTodayBirthdayParsing() {
        val today = LocalDateTime(2024, 1, 2, 8, 0)

        BirthdayService.birthdays = listOf(
            BirthdayService.Birthday("Test1 | Birthday", LocalDate(2024, 1, 1), 1, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test2 | Birthday", LocalDate(2024, 1, 2), 2, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test3 | Birthday", LocalDate(2024, 1, 1), 3, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test4 | Birthday", LocalDate(2024, 1, 3), 4, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test5 | Birthday", LocalDate(2024, 1, 1), 5, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test6 | Birthday", LocalDate(2024, 1, 4), 6, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test7 | Birthday", LocalDate(2024, 1, 1), 7, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test8 | Birthday", LocalDate(2024, 1, 5), 8, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test9 | Birthday", LocalDate(2024, 1, 1), 9, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test10 | Birthday", LocalDate(2024, 1, 6), 10, recurrenceSet = emptySet()),
            BirthdayService.Birthday("Test11 | Birthday", LocalDate(2024, 1, 2), 11, recurrenceSet = emptySet())
        )

        assertEquals(
            listOf("Test2", "Test11"),
            BirthdayService.getTodayBirthdays(today).map { it.username }
        )
    }
}