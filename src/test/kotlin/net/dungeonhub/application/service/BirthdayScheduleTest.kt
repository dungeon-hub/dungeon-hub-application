package net.dungeonhub.application.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BirthdayScheduleTest {
    private val runDuration = 3.seconds
    private val errors = mutableListOf<Exception>()

    /** Runs [block] in its own coroutine and hands it a function that stops it (like a cancelled scheduler would). */
    private fun runLoop(block: suspend (stop: () -> Unit) -> Unit) = runBlocking {
        lateinit var job: Job
        job = launch { block { job.cancel() } }
        job.join()
    }

    /**
     * Runs the daily loop on a virtual clock and returns the instants at which the action started.
     * Every run takes [runDuration]; the loop is stopped after [runs] runs.
     */
    private fun simulate(
        start: String,
        zone: String,
        hour: Int,
        runs: Int,
        onRun: suspend (Int) -> Unit = {}
    ): List<Instant> {
        var clock = Instant.parse(start)
        val fired = mutableListOf<Instant>()
        errors.clear()

        runLoop { stop ->
            BirthdayService.runDaily(
                TimeZone.of(zone), hour, "test",
                now = { clock },
                sleep = { clock += it },
                onError = { errors += it }
            ) {
                fired += clock
                clock += runDuration

                try {
                    onRun(fired.size)
                } finally {
                    if (fired.size >= runs) stop()
                }
            }
        }

        return fired
    }

    private val zones = listOf(
        "UTC", "Europe/Berlin", "America/New_York", "Australia/Lord_Howe", "Australia/Sydney",
        "Asia/Kolkata", "Pacific/Kiritimati", "Pacific/Auckland", "America/Santiago", "America/Havana",
        "Africa/Casablanca", "-05:00", "+05:30"
    )

    @Test
    fun testExactlyOneRunPerLocalDayForTwoYears() {
        for (zone in zones) {
            for (hour in listOf(0, 9)) {
                val timeZone = TimeZone.of(zone)
                val fired = simulate("2024-01-01T12:00:00Z", zone, hour, runs = 800)

                assertEquals(800, fired.size)

                val localDates = fired.map { it.toLocalDateTime(timeZone) }

                localDates.zipWithNext().forEach { (a, b) ->
                    assertEquals(
                        1, a.date.daysUntil(b.date),
                        "Expected exactly one run per day in $zone at $hour, but got $a followed by $b"
                    )
                }

                // Runs happen exactly at the target hour. The only exception are days on which that
                // time does not exist locally because the clock jumps over it (at most once per year); then it runs right after the gap.
                val offHours = localDates.filter { it.hour != hour || it.minute != 0 || it.second != 0 }
                assertTrue(
                    offHours.size <= 3,
                    "Unexpected run times in $zone at $hour: $offHours"
                )
                offHours.forEach { assertTrue(it.hour in hour..hour + 1, "Run too far from $hour:00 in $zone: $it") }
            }
        }
    }

    @Test
    fun testRunStaysAtTheSameLocalTimeAfterDaylightSaving() {
        val berlin = TimeZone.of("Europe/Berlin")

        // runs on 03-29, 03-30, 03-31 (spring forward), 04-01 and 04-02 must all be at local midnight
        val fired = simulate("2024-03-29T12:00:00Z", "Europe/Berlin", 0, runs = 5)

        assertEquals(
            listOf(
                "2024-03-30T00:00", "2024-03-31T00:00", "2024-04-01T00:00", "2024-04-02T00:00", "2024-04-03T00:00"
            ),
            fired.map { it.toLocalDateTime(berlin).toString() }
        )
    }

    @Test
    fun testFirstRunIsImmediateWhenStartedExactlyOnTime() {
        val fired = simulate("2024-01-02T00:00:00Z", "UTC", 0, runs = 3)

        assertEquals(
            listOf(
                Instant.parse("2024-01-02T00:00:00Z"),
                Instant.parse("2024-01-03T00:00:00Z"),
                Instant.parse("2024-01-04T00:00:00Z")
            ),
            fired
        )
    }

    @Test
    fun testStartingJustAfterTheHourWaitsForTheNextDay() {
        val fired = simulate("2024-01-02T00:00:01Z", "UTC", 0, runs = 1)

        assertEquals(listOf(Instant.parse("2024-01-03T00:00:00Z")), fired)
    }

    @Test
    fun testRunsBeforeTheHourWaitForTheSameDay() {
        val fired = simulate("2024-01-02T23:59:59Z", "UTC", 0, runs = 1)

        assertEquals(listOf(Instant.parse("2024-01-03T00:00:00Z")), fired)

        val sameDay = simulate("2024-01-02T08:00:00Z", "UTC", 9, runs = 1)

        assertEquals(listOf(Instant.parse("2024-01-02T09:00:00Z")), sameDay)
    }

    @Test
    fun testSlowRunsDoNotCauseASkippedOrDoubleRun() {
        var clock = Instant.parse("2024-01-01T12:00:00Z")
        val fired = mutableListOf<Instant>()

        runLoop { stop ->
            BirthdayService.runDaily(TimeZone.UTC, 0, "test", now = { clock }, sleep = { clock += it }) {
                fired += clock
                // a run that takes almost an hour must still end up with one run per day
                clock += 59.seconds * 60

                if (fired.size >= 5) stop()
            }
        }

        assertEquals(
            List(5) { Instant.parse("2024-01-02T00:00:00Z") + (24 * it).hours },
            fired
        )
    }

    @Test
    fun testDelayWhenTheTargetTimeIsSkippedByDaylightSaving() {
        // Havana jumps from 00:00 to 01:00 on 2024-03-10, so midnight doesn't exist that day.
        val havana = TimeZone.of("America/Havana")

        val delay = BirthdayService.calculateDelayUntil(
            Instant.parse("2024-03-09T05:00:03Z"), havana, 0, allowImmediate = false
        )

        assertTrue(delay > Duration.ZERO)
        assertEquals(
            LocalDate(2024, 3, 10),
            (Instant.parse("2024-03-09T05:00:03Z") + delay).toLocalDateTime(havana).date
        )
    }

    @Test
    fun testNoSecondRunWhenTheTargetTimeHappensTwice() {
        // Havana turns the clocks back from 01:00 to 00:00 on 2024-11-03, so local midnight happens twice.
        val havana = TimeZone.of("America/Havana")
        val fired = simulate("2024-11-02T12:00:00Z", "America/Havana", 0, runs = 4)

        val localDates = fired.map { it.toLocalDateTime(havana).date }

        assertEquals(
            listOf(LocalDate(2024, 11, 3), LocalDate(2024, 11, 4), LocalDate(2024, 11, 5), LocalDate(2024, 11, 6)),
            localDates
        )
    }

    @Test
    fun testDelayIsNeverNegativeAndNeverMoreThanOneDayAndAnHour() {
        val start = Instant.parse("2024-01-01T00:00:00Z")

        for (zone in zones) {
            val timeZone = TimeZone.of(zone)

            for (hour in listOf(0, 9)) {
                // every 7 minutes over 400 days covers all sorts of offsets to the target time
                var now = start
                while (now < start + (400 * 24).hours) {
                    for (immediate in listOf(true, false)) {
                        val delay = BirthdayService.calculateDelayUntil(now, timeZone, hour, immediate)

                        assertTrue(delay >= Duration.ZERO, "Negative delay at $now in $zone")
                        assertTrue(delay <= 25.hours + 1.seconds, "Delay too long at $now in $zone: $delay")
                    }
                    now += (7 * 60 + 13).seconds
                }
            }
        }
    }

    @Test
    fun testFailingActionDoesNotStopTheLoop() {
        val fired = simulate("2024-01-01T12:00:00Z", "UTC", 0, runs = 5) { run ->
            if (run == 2 || run == 3) throw IllegalStateException("Discord is down")
        }

        // all five days were attempted, even though two of them failed
        assertEquals(5, fired.size)
        assertEquals(2, errors.size)
        assertEquals(
            List(5) { Instant.parse("2024-01-02T00:00:00Z") + (24 * it).hours },
            fired
        )
    }

    @Test
    fun testCancellationStopsTheLoop() {
        var runs = 0

        assertFailsWith<CancellationException> {
            runBlocking {
                var clock = Instant.parse("2024-01-01T12:00:00Z")

                BirthdayService.runDaily(TimeZone.UTC, 0, "test", now = { clock }, sleep = { clock += it }) {
                    runs++
                    throw CancellationException("Application was restarted.")
                }
            }
        }

        assertEquals(1, runs)
    }

    private fun birthdaysIn(vararg timezones: String?) = timezones.mapIndexed { index, timezone ->
        BirthdayService.Birthday("User$index | Birthday", LocalDate(2000, 1, 1), index.toLong(), timezone = timezone)
    }

    @Test
    fun testEachTimezoneIsOnlyScheduledOnce() {
        BirthdayService.resetScheduledTimezones()
        BirthdayService.birthdays = birthdaysIn("Europe/Berlin", "Asia/Tokyo", "Europe/Berlin", null, "Asia/Tokyo")

        assertEquals(
            listOf("Europe/Berlin" to TimeZone.of("Europe/Berlin"), "Asia/Tokyo" to TimeZone.of("Asia/Tokyo")),
            BirthdayService.claimNewTimezones()
        )

        // refreshing the same data (which happens after every announcement) must not schedule anything again
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())
    }

    @Test
    fun testOnlyNewTimezonesAreScheduledAfterADataRefresh() {
        BirthdayService.resetScheduledTimezones()
        BirthdayService.birthdays = birthdaysIn("Europe/Berlin", null)
        assertEquals(listOf("Europe/Berlin"), BirthdayService.claimNewTimezones().map { it.first })

        BirthdayService.birthdays = birthdaysIn("Europe/Berlin", "America/New_York", "Europe/Berlin")
        assertEquals(listOf("America/New_York"), BirthdayService.claimNewTimezones().map { it.first })

        // removing a timezone from the data doesn't schedule anything, and adding it back doesn't duplicate it
        BirthdayService.birthdays = birthdaysIn(null)
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())

        BirthdayService.birthdays = birthdaysIn("America/New_York")
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())
    }

    @Test
    fun testInvalidTimezonesAreNeverScheduled() {
        BirthdayService.resetScheduledTimezones()
        // can only happen when a Birthday is built by hand, fromComponent already drops invalid zones
        BirthdayService.birthdays = birthdaysIn("Middle-earth", "", "Europe/Berlin")

        assertEquals(listOf("Europe/Berlin"), BirthdayService.claimNewTimezones().map { it.first })
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())
    }

    @Test
    fun testDifferentSpellingsOfTheSameZoneKeepTheirOwnSchedule() {
        BirthdayService.resetScheduledTimezones()
        BirthdayService.birthdays = birthdaysIn("+0530", "+05:30")

        // today's birthdays are matched by the exact timezone text, so each spelling needs its own schedule
        assertEquals(listOf("+0530", "+05:30"), BirthdayService.claimNewTimezones().map { it.first })
    }

    @Test
    fun testResetAllowsSchedulingAgainAfterARestart() {
        BirthdayService.resetScheduledTimezones()
        BirthdayService.birthdays = birthdaysIn("Europe/Berlin")
        assertEquals(1, BirthdayService.claimNewTimezones().size)

        BirthdayService.resetScheduledTimezones()
        assertEquals(1, BirthdayService.claimNewTimezones().size)
    }

    @Test
    fun testNoTimezonesMeansNothingToSchedule() {
        BirthdayService.resetScheduledTimezones()
        BirthdayService.birthdays = emptyList()
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())

        BirthdayService.birthdays = birthdaysIn(null, null)
        assertEquals(emptyList(), BirthdayService.claimNewTimezones())
    }
}
