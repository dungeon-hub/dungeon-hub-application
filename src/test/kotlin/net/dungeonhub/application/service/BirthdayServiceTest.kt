package net.dungeonhub.application.service

import kotlinx.datetime.*
import net.fortuna.ical4j.data.CalendarBuilder
import java.io.StringReader
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BirthdayServiceTest {
    private fun delay(now: String, zone: String, hour: Int, allowImmediate: Boolean = true) =
        BirthdayService.calculateDelayUntil(Instant.parse(now), TimeZone.of(zone), hour, allowImmediate)

    @Test
    fun testExecutionTimeCalculation() {
        assertEquals(0.seconds, delay("2024-01-02T09:00:00Z", "UTC", 9))
        assertEquals(1.hours, delay("2024-01-02T08:00:00Z", "UTC", 9))
        assertEquals(Duration.parse("22m 38s"), delay("2024-01-02T08:37:22Z", "UTC", 9))
        assertEquals(23.hours, delay("2024-01-02T10:00:00Z", "UTC", 9))
        assertEquals(Duration.parse("22h 46m 59s"), delay("2024-01-02T10:13:01Z", "UTC", 9))

        // Past the execution hour but within the same hour must not produce a negative delay.
        assertEquals(Duration.parse("23h 30m"), delay("2024-01-02T09:30:00Z", "UTC", 9))
    }

    @Test
    fun testImmediateExecutionOnlyAllowedOnce() {
        assertEquals(0.seconds, delay("2024-01-02T00:00:00Z", "UTC", 0, allowImmediate = true))
        assertEquals(24.hours, delay("2024-01-02T00:00:00Z", "UTC", 0, allowImmediate = false))
    }

    @Test
    fun testMidnightExecutionTime() {
        assertEquals(24.hours - 30.minutes, delay("2024-01-02T00:30:00Z", "UTC", 0))
        assertEquals(1.hours, delay("2024-01-02T23:00:00Z", "UTC", 0))
        assertEquals(Duration.parse("9h 30m"), delay("2024-01-02T14:30:00Z", "UTC", 0))
    }

    @Test
    fun testTimezoneBirthdayMidnightDelay() {
        // 2024-01-01T21:30Z is 22:30 in Berlin, 06:30 (next day) in Tokyo and 16:30 at UTC-5.
        val now = "2024-01-01T21:30:00Z"

        assertEquals(1.hours + 30.minutes, delay(now, "Europe/Berlin", 0))
        assertEquals(17.hours + 30.minutes, delay(now, "Asia/Tokyo", 0))
        assertEquals(7.hours + 30.minutes, delay(now, "-05:00", 0))
    }

    @Test
    fun testDelayAcrossSpringForward() {
        // Berlin switches from CET to CEST on 2024-03-31 at 02:00, so that day only has 23 hours.
        // 2024-03-30T23:00Z is 00:00 on the 31st in Berlin; the next midnight is 2024-03-31T22:00Z.
        assertEquals(23.hours, delay("2024-03-30T23:00:00Z", "Europe/Berlin", 0, allowImmediate = false))
    }

    @Test
    fun testDelayAcrossFallBack() {
        // Berlin switches back from CEST to CET on 2024-10-27 at 03:00, so that day has 25 hours.
        // 2024-10-26T22:00Z = 00:00 on the 27th in Berlin; the next midnight is 2024-10-27T23:00Z.
        assertEquals(25.hours, delay("2024-10-26T22:00:00Z", "Europe/Berlin", 0, allowImmediate = false))
    }

    @Test
    fun testServerTimeDelayAcrossDaylightSaving() {
        // 01:00 local on 2024-03-31 in Berlin is 1h before the clock jumps, so 9:00 is only 7h away in real time.
        assertEquals(7.hours, delay("2024-03-31T00:00:00Z", "Europe/Berlin", 9))
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
    fun testTimeZoneParsing() {
        assertNotNull(BirthdayService.parseTimeZone("Europe/Berlin"))

        assertNotNull(BirthdayService.parseTimeZone("+0530"))

        assertNotNull(BirthdayService.parseTimeZone("+05:30"))

        assertNull(BirthdayService.parseTimeZone("Middle-earth"))

        assertNull(BirthdayService.parseTimeZone(""))
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

    @Test
    fun testTimeZoneParsingDetails() {
        assertEquals(TimeZone.of("Europe/Berlin"), BirthdayService.parseTimeZone("  Europe/Berlin  "))
        assertEquals(TimeZone.of("+05:30"), BirthdayService.parseTimeZone("+0530"))
        assertEquals(TimeZone.of("+05:30"), BirthdayService.parseTimeZone("+05:30"))
        assertEquals(TimeZone.of("-08:00"), BirthdayService.parseTimeZone("-0800"))
        assertEquals(TimeZone.of("-08:00"), BirthdayService.parseTimeZone("-08:00"))
        assertEquals(TimeZone.UTC, BirthdayService.parseTimeZone("UTC"))

        assertNull(BirthdayService.parseTimeZone("   "))
        assertNull(BirthdayService.parseTimeZone("+25:00"))
        assertNull(BirthdayService.parseTimeZone("+12ab"))
        assertNull(BirthdayService.parseTimeZone("Europe/Atlantis"))
    }

    @Test
    fun testTodayBirthdaysAreSeparatedByTimezone() {
        BirthdayService.birthdays = listOf(
            BirthdayService.Birthday("NoZone | Birthday", LocalDate(2024, 1, 2), 1),
            BirthdayService.Birthday("Berlin | Birthday", LocalDate(2024, 1, 2), 2, timezone = "Europe/Berlin"),
            BirthdayService.Birthday("Tokyo | Birthday", LocalDate(2024, 1, 2), 3, timezone = "Asia/Tokyo"),
            BirthdayService.Birthday("BerlinOther | Birthday", LocalDate(2024, 1, 5), 4, timezone = "Europe/Berlin"),
        )

        val today = LocalDateTime(2024, 1, 2, 0, 0)

        assertEquals(listOf("NoZone"), BirthdayService.getTodayBirthdays(today).map { it.username })
        assertEquals(listOf("NoZone"), BirthdayService.getTodayBirthdays(today, null).map { it.username })
        assertEquals(listOf("Berlin"), BirthdayService.getTodayBirthdays(today, "Europe/Berlin").map { it.username })
        assertEquals(listOf("Tokyo"), BirthdayService.getTodayBirthdays(today, "Asia/Tokyo").map { it.username })
        assertEquals(emptyList(), BirthdayService.getTodayBirthdays(today, "America/New_York"))
    }

    @Test
    fun testBirthdayIsTodayDependsOnTheTimezoneLocalDate() {
        BirthdayService.birthdays = listOf(
            BirthdayService.Birthday("Tokyo | Birthday", LocalDate(2024, 1, 2), 1, timezone = "Asia/Tokyo"),
            BirthdayService.Birthday("Berlin | Birthday", LocalDate(2024, 1, 2), 2, timezone = "Europe/Berlin"),
        )

        // 2024-01-01T16:00Z is already Jan 2nd (01:00) in Tokyo, but still Jan 1st (17:00) in Berlin.
        val instant = Instant.parse("2024-01-01T16:00:00Z")
        val tokyo = TimeZone.of("Asia/Tokyo")
        val berlin = TimeZone.of("Europe/Berlin")

        assertEquals(
            listOf("Tokyo"),
            BirthdayService.getTodayBirthdays(instant.toLocalDateTime(tokyo), "Asia/Tokyo").map { it.username }
        )
        assertEquals(
            emptyList(),
            BirthdayService.getTodayBirthdays(instant.toLocalDateTime(berlin), "Europe/Berlin")
        )
    }

    private fun parseEvent(summary: String = "Test | Birthday", description: String?): BirthdayService.Birthday? {
        val descriptionLine = description?.let { "DESCRIPTION:$it\r\n" } ?: ""
        val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\nBEGIN:VEVENT\r\n" +
                "UID:1@test\r\nDTSTAMP:20240101T000000Z\r\nDTSTART;VALUE=DATE:20240102\r\n" +
                "RRULE:FREQ=YEARLY\r\nSUMMARY:$summary\r\nLOCATION:1234\r\n$descriptionLine" +
                "END:VEVENT\r\nEND:VCALENDAR\r\n"

        val calendar = CalendarBuilder().build(StringReader(ics))

        return calendar.componentList.all.firstNotNullOfOrNull { BirthdayService.Birthday.fromComponent(it) }
    }

    @Test
    fun testBirthdayComponentTimezoneParsing() {
        val withZone = parseEvent(description = "1995\\nEurope/Berlin")
        assertNotNull(withZone)
        assertEquals(1995, withZone.birthYear)
        assertEquals("Europe/Berlin", withZone.timezone)

        val withOffset = parseEvent(description = "1995\\n+0530")
        assertEquals("+0530", withOffset?.timezone)

        val invalidZone = parseEvent(description = "1995\\nMiddle-earth")
        assertNotNull(invalidZone)
        assertEquals(1995, invalidZone.birthYear)
        assertNull(invalidZone.timezone)

        val yearOnly = parseEvent(description = "1995")
        assertEquals(1995, yearOnly?.birthYear)
        assertNull(yearOnly?.timezone)

        val noDescription = parseEvent(description = null)
        assertNotNull(noDescription)
        assertNull(noDescription.birthYear)
        assertNull(noDescription.timezone)
    }

    private fun parseProperties(vararg properties: String): BirthdayService.Birthday? {
        val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\nBEGIN:VEVENT\r\n" +
                "UID:1@test\r\nDTSTAMP:20240101T000000Z\r\n" +
                properties.joinToString("") { "$it\r\n" } +
                "END:VEVENT\r\nEND:VCALENDAR\r\n"

        return CalendarBuilder().build(StringReader(ics)).componentList.all
            .firstNotNullOfOrNull { BirthdayService.Birthday.fromComponent(it) }
    }

    private val validProperties = arrayOf("DTSTART;VALUE=DATE:20240102", "SUMMARY:Test | Birthday", "LOCATION:1234")

    @Test
    fun testComponentDescriptionEdgeCases() {
        fun parse(description: String) = parseProperties(*validProperties, "DESCRIPTION:$description")

        // whitespace around the values is ignored
        parse("  1995  \\n  Europe/Berlin  ").let {
            assertEquals(1995, it?.birthYear)
            assertEquals("Europe/Berlin", it?.timezone)
        }

        // an invalid year doesn't stop the timezone from being read
        parse("unknown\\nEurope/Berlin").let {
            assertNull(it?.birthYear)
            assertEquals("Europe/Berlin", it?.timezone)
        }

        // an empty second line means no timezone
        assertNull(parse("1995\\n")?.timezone)
        assertNull(parse("1995\\n   ")?.timezone)

        // further lines are ignored
        parse("1995\\nEurope/Berlin\\nsome notes").let {
            assertEquals(1995, it?.birthYear)
            assertEquals("Europe/Berlin", it?.timezone)
        }

        // the timezone has to be on the second line, a timezone on the first line is not picked up
        parse("Europe/Berlin").let {
            assertNull(it?.birthYear)
            assertNull(it?.timezone)
        }

        // a lowercase zone id isn't valid, so the birthday falls back to server time
        assertNull(parse("1995\\neurope/berlin")?.timezone)
    }

    @Test
    fun testComponentRequiredProperties() {
        assertNotNull(parseProperties(*validProperties))

        assertNull(parseProperties("SUMMARY:Test | Birthday", "LOCATION:1234"))
        assertNull(parseProperties("DTSTART;VALUE=DATE:20240102", "LOCATION:1234"))
        assertNull(parseProperties("DTSTART;VALUE=DATE:20240102", "SUMMARY:Test | Birthday"))
        assertNull(parseProperties("DTSTART;VALUE=DATE:20240102", "SUMMARY:Test | Birthday", "LOCATION:not-a-number"))
    }

    @Test
    fun testRecurringBirthdayWithTimezoneIsTodayAtAnyTimeOfTheDay() {
        // Pick a day half a year from now so it's inside the calculated recurrence window.
        var target = java.time.LocalDate.now().plusMonths(6)
        if (target.monthValue == 2 && target.dayOfMonth == 29) target = target.plusDays(1)
        val next = target.plusDays(1)
        val previous = target.minusDays(1)

        // All-day calendar events end on the following day, like in the real calendar.
        val birthday = parseProperties(
            "DTSTART;VALUE=DATE:2000%02d%02d".format(target.monthValue, target.dayOfMonth),
            "DTEND;VALUE=DATE:2000%02d%02d".format(next.monthValue, next.dayOfMonth),
            "SUMMARY:Test | Birthday", "LOCATION:1234", "RRULE:FREQ=YEARLY", "DESCRIPTION:2000\\nAsia/Tokyo"
        )
        assertNotNull(birthday)
        assertEquals("Asia/Tokyo", birthday.timezone)

        // The announcement runs right after midnight (with a few milliseconds of delay), so those must match.
        for (time in listOf(
            LocalTime(0, 0, 0), LocalTime(0, 0, 0, 3_000_000), LocalTime(0, 0, 1), LocalTime(9, 0, 0), LocalTime(12, 0), LocalTime(23, 59, 59)
        )) {
            assertTrue(
                birthday.isToday(LocalDateTime(LocalDate(target.year, target.monthValue, target.dayOfMonth), time)),
                "Expected the birthday to be today at $time"
            )
        }

        // ...but not shortly before midnight of the previous day, nor shortly after midnight of the next day.
        assertFalse(birthday.isToday(
            LocalDateTime(LocalDate(previous.year, previous.monthValue, previous.dayOfMonth), LocalTime(23, 59, 59, 900_000_000))
        ))
        assertFalse(birthday.isToday(
            LocalDateTime(LocalDate(next.year, next.monthValue, next.dayOfMonth), LocalTime(0, 0, 0, 3_000_000))
        ))
    }

    @Test
    fun testUsernameParsing() {
        assertEquals("Test", BirthdayService.Birthday("Test | Birthday", LocalDate(2024, 1, 1), 1).username)
        assertEquals("Test", BirthdayService.Birthday("Test", LocalDate(2024, 1, 1), 1).username)
    }

    @Test
    fun testOnlyLatestBirthdayEntryOfAUserIsUsed() {
        BirthdayService.birthdays = listOf(
            BirthdayService.Birthday("Old | Birthday", LocalDate(2023, 1, 2), 1, timezone = "Europe/Berlin"),
            BirthdayService.Birthday("New | Birthday", LocalDate(2024, 1, 2), 1, timezone = "Asia/Tokyo"),
        )

        val today = LocalDateTime(2024, 1, 2, 0, 0)

        // the user moved to a new timezone, so they must not be announced for the old one
        assertEquals(emptyList(), BirthdayService.getTodayBirthdays(today, "Europe/Berlin"))
        assertEquals(listOf("New"), BirthdayService.getTodayBirthdays(today, "Asia/Tokyo").map { it.username })
        assertEquals(emptyList(), BirthdayService.getTodayBirthdays(today, null))
    }

    @Test
    fun testTodayBirthdaysAcrossYearBoundary() {
        BirthdayService.birthdays = listOf(
            BirthdayService.Birthday("NewYearsEve | Birthday", LocalDate(2000, 12, 31), 1, timezone = "Pacific/Kiritimati"),
            BirthdayService.Birthday("NewYearsDay | Birthday", LocalDate(2000, 1, 1), 2, timezone = "Pacific/Kiritimati"),
        )

        // 2024-12-31T10:30Z is already Jan 1st in Kiritimati (UTC+14).
        val instant = Instant.parse("2024-12-31T10:30:00Z")
        val kiritimati = instant.toLocalDateTime(TimeZone.of("Pacific/Kiritimati"))

        assertEquals(listOf("NewYearsDay"), BirthdayService.getTodayBirthdays(kiritimati, "Pacific/Kiritimati").map { it.username })
        assertEquals(
            listOf("NewYearsEve"),
            BirthdayService.getTodayBirthdays(instant.toLocalDateTime(TimeZone.UTC), "Pacific/Kiritimati").map { it.username }
        )
    }

    @Test
    fun testDescriptionWithYearAndTimezone() {
        val birthday = BirthdayService.Birthday("Alice | Birthday", LocalDate(1995, 3, 7), 42, 1995, "Europe/Berlin")

        val lines = BirthdayService.buildDescription(birthday, LocalDateTime(2024, 3, 7, 0, 0)).split("\n")

        assertEquals(5, lines.size)
        assertTrue(lines[0].startsWith("Happy Birthday, Alice (<@42>)!"))
        assertEquals("Today, they are turning 29 years old!", lines[1])
        assertEquals("Their birthday is on 07.03.", lines[2])
        assertEquals("Announced at midnight in Europe/Berlin.", lines[3])
        assertTrue(lines[4].startsWith("Make sure to congratulate them in <#"))
    }

    @Test
    fun testDescriptionWithoutYearAndTimezone() {
        val birthday = BirthdayService.Birthday("Bob | Birthday", LocalDate(2000, 12, 31), 7)

        val lines = BirthdayService.buildDescription(birthday, LocalDateTime(2024, 12, 31, 9, 0)).split("\n")

        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("Happy Birthday, Bob (<@7>)!"))
        assertEquals("Their birthday is on 31.12.", lines[1])
        assertTrue(lines[2].startsWith("Make sure to congratulate them in <#"))
    }

    @Test
    fun testDescriptionWithTimezoneButNoYear() {
        val birthday = BirthdayService.Birthday("Cara | Birthday", LocalDate(2000, 1, 2), 9, timezone = "+05:30")

        val lines = BirthdayService.buildDescription(birthday, LocalDateTime(2024, 1, 2, 0, 0)).split("\n")

        assertEquals(4, lines.size)
        assertEquals("Their birthday is on 02.01.", lines[1])
        assertEquals("Announced at midnight in +05:30.", lines[2])
    }

    @Test
    fun testDescriptionAgeUsesTheLocalYear() {
        val birthday = BirthdayService.Birthday("Dan | Birthday", LocalDate(2000, 1, 1), 5, 2000, "Pacific/Kiritimati")

        // In UTC it's still 2024, but in Kiritimati the new year has begun.
        val instant = Instant.parse("2024-12-31T10:30:00Z")
        val local = instant.toLocalDateTime(TimeZone.of("Pacific/Kiritimati"))

        assertTrue(BirthdayService.buildDescription(birthday, local).contains("turning 25 years old"))
    }

    @Test
    fun testTimeZoneParsingNeverThrows() {
        listOf(
            "+", "-", "++0530", "+05:3", "+5", "+05", "Z", "z", "+99999999", "Europe/", "/", "\u0000", "a".repeat(10_000),
            "+0560", "+2500", "+05:60", "-0000", "+0000", "+1400", "-1200", "UTC+1", "GMT", "EST", "CET"
        ).forEach {
            // must either parse or return null, never throw
            BirthdayService.parseTimeZone(it)
        }

        assertNull(BirthdayService.parseTimeZone("+0560"))
        assertNull(BirthdayService.parseTimeZone("+2500"))
        assertNull(BirthdayService.parseTimeZone("+05:60"))
        assertNull(BirthdayService.parseTimeZone("++0530"))
        assertEquals(TimeZone.of("+14:00"), BirthdayService.parseTimeZone("+1400"))
        assertEquals(TimeZone.of("-12:00"), BirthdayService.parseTimeZone("-1200"))
    }
}
