package net.dungeonhub.application.service

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.utils.permissionsForMember
import dev.kordex.core.utils.scheduling.Scheduler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.client.DungeonHubClient
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Period
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@OnStart
object BirthdayService : StartupListener {
    private const val BIRTHDAY_CALENDAR_URL =
        "https://cloud.dungeon-hub.net/remote.php/dav/public-calendars/g2tZRB2YpacJtAtt?export"
    private const val BIRTHDAY_PING_ROLE = 1362023553066860594
    private const val BIRTHDAYS_CHANNEL = 1362024376974839908
    private const val BIRTHDAY_CONGRATS_CHANNEL = 1082583379226148874
    private const val EXECUTION_HOUR = 9
    private const val TIMEZONE_EXECUTION_HOUR = 0
    private val logger = LoggerFactory.getLogger(BirthdayService::class.java)
    private lateinit var scheduler: Scheduler
    @Volatile
    var birthdays: List<Birthday> = listOf()
    private val scheduledTimezones: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val timezoneJobs = ConcurrentHashMap<String, Job>()

    override suspend fun postStart() {
        if (::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        resetScheduledTimezones()
        scheduler = DhScheduler()

        updateBirthdayData()

        scheduleServerTimeBirthdays()

        scheduleNewTimezones()
    }

    private fun scheduleServerTimeBirthdays() {
        launchDailyAt(TimeZone.currentSystemDefault(), EXECUTION_HOUR, null)
    }

    /**
     * One schedule per distinct timezone, so each fires at midnight (start of the day) in that timezone.
     * Timezones that already have a schedule are skipped.
     */
    private fun scheduleNewTimezones() {
        for ((name, timeZone) in claimNewTimezones()) {
            timezoneJobs[name] = launchDailyAt(timeZone, TIMEZONE_EXECUTION_HOUR, name)
        }
    }

    /**
     * Cancels the schedules of timezones that no longer appear in the current [birthdays].
     * Schedules for timezones that are still present are kept.
     */
    internal fun cancelStaleTimezones() {
        val current = birthdays.mapNotNull { it.timezone }.toSet()

        for (name in scheduledTimezones.toList()) {
            if (name in current) continue

            scheduledTimezones.remove(name)
            timezoneJobs.remove(name)?.cancel("Timezone $name is no longer used by any birthday.")
        }
    }

    /**
     * Returns the timezones of the current [birthdays] that don't have a schedule yet and marks them as scheduled.
     * Timezones that can't be parsed are never returned.
     */
    internal fun claimNewTimezones(): List<Pair<String, TimeZone>> {
        return birthdays.mapNotNull { it.timezone }.distinct().mapNotNull { name ->
            if (!scheduledTimezones.add(name)) return@mapNotNull null

            parseTimeZone(name)?.let { name to it }
        }
    }

    internal fun resetScheduledTimezones() {
        scheduledTimezones.clear()
        timezoneJobs.clear()
    }

    /**
     * Announces the birthdays every day at [hour] o'clock in [timeZone].
     * The delay is recalculated after every run (instead of repeating a fixed 24 hours),
     * so daylight saving changes can't move the announcement to the wrong day.
     */
    private fun launchDailyAt(timeZone: TimeZone, hour: Int, timezoneName: String?): Job {
        return scheduler.launch {
            runDaily(timeZone, hour, timezoneName ?: "server time") {
                updateBirthdayData()

                sendBirthdays(timeZone, timezoneName)
            }
        }
    }

    /**
     * Runs [action] every day at [hour] o'clock in [timeZone] until the coroutine is cancelled.
     * A failing [action] is logged and doesn't stop the loop.
     * [now] and [sleep] can be replaced to test the loop without waiting.
     */
    internal suspend fun runDaily(
        timeZone: TimeZone,
        hour: Int,
        name: String,
        now: () -> Instant = { Clock.System.now() },
        sleep: suspend (Duration) -> Unit = { delay(it) },
        onError: (Exception) -> Unit = { logger.error("Birthday announcement failed for $name.", it) },
        action: suspend () -> Unit
    ) {
        var first = true

        while (currentCoroutineContext().isActive) {
            sleep(calculateDelayUntil(now(), timeZone, hour, allowImmediate = first))
            first = false

            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                onError(exception)
            }
        }
    }

    /**
     * Time from [now] until the next [hour]:00 in [timeZone].
     * Works on real instants, so days with a daylight saving change (23 or 25 hours) are handled.
     */
    fun calculateDelayUntil(now: Instant, timeZone: TimeZone, hour: Int, allowImmediate: Boolean = true): Duration {
        val today = now.toLocalDateTime(timeZone).date
        var target = LocalDateTime(today, LocalTime(hour, 0)).toInstant(timeZone)

        if (target < now || (target == now && !allowImmediate)) {
            target = LocalDateTime(today.plus(1, DateTimeUnit.DAY), LocalTime(hour, 0)).toInstant(timeZone)
        }

        return target - now
    }

    /**
     * Announce the birthdays that are today in the given timezone.
     * When [timezoneName] is set, only birthdays with that timezone are announced;
     * otherwise only birthdays without a timezone (server time).
     */
    private suspend fun sendBirthdays(timeZone: TimeZone, timezoneName: String?) {
        val now = Clock.System.now().toLocalDateTime(timeZone)
        val todayBirthdays = getTodayBirthdays(now, timezoneName)
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        val birthdayChannel = DiscordConnection.bot.kordRef.getChannelOf<GuildMessageChannel>(Snowflake(BIRTHDAYS_CHANNEL))

        if (birthdayChannel == null) {
            logger.error("Couldn't find the birthday channel.")
            return
        }

        for (birthday in todayBirthdays) {
            val birthdayUser = DiscordConnection.bot.kordRef.getUser(Snowflake(birthday.userId))?.asMemberOrNull(birthdayChannel.guildId)

            if (birthdayUser == null || !birthdayChannel.permissionsForMember(birthdayUser).contains(Permission.ViewChannel)) {
                logger.error("Birthday user doesn't have access to the birthday channel. Skipping ${birthday.userId}/${birthdayUser?.effectiveName} with permissions ${birthdayUser?.let { birthdayChannel.permissionsForMember(it) }}.")
                continue
            }

            val embed = EmbedBuilder()
            embed.color(EmbedColor.Positive)
            embed.title = birthday.eventName
            embed.description = buildDescription(birthday, now)

            embeds += embed
        }

        if (embeds.isNotEmpty()) {
            birthdayChannel.createMessage {
                this.content = "<@&$BIRTHDAY_PING_ROLE>"
                this.embeds = embeds
            }
        }
    }

    internal fun buildDescription(birthday: Birthday, now: LocalDateTime): String {
        val age = birthday.birthYear?.let { "\nToday, they are turning ${now.year - it} years old!" } ?: ""
        val day = birthday.date.day.toString().padStart(2, '0')
        val month = birthday.date.month.number.toString().padStart(2, '0')
        val timezone = birthday.timezone?.let { "\nAnnounced at midnight in $it." } ?: ""

        return "Happy Birthday, ${birthday.username} (<@${birthday.userId}>)! \uD83C\uDF89 \uD83E\uDD73 ❤\uFE0F" +
                age +
                "\nTheir birthday is on $day.$month." +
                timezone +
                "\nMake sure to congratulate them in <#$BIRTHDAY_CONGRATS_CHANNEL>!"
    }

    fun getTodayBirthdays(today: LocalDateTime, timezoneName: String? = null): List<Birthday> {
        return birthdays.groupBy { it.userId }.map { it.value.maxBy { birthday -> birthday.date.year } }
            .filter { it.isToday(today) && it.timezone == timezoneName }
    }

    suspend fun updateBirthdayData() {
        try {
            DungeonHubClient().executeRawRequest {
                url(Url(BIRTHDAY_CALENDAR_URL))
            }?.takeIf { it.status.isSuccess() }?.bodyAsBytes()?.takeIf { it.isNotEmpty() }?.let {
                CalendarBuilder().build(ByteArrayInputStream(it))?.let { calendar ->
                    val birthdayList = calendar.componentList.all.mapNotNull { component ->
                        Birthday.fromComponent(component)
                    }

                    if (birthdayList.isNotEmpty()) {
                        birthdays = birthdayList

                        // Reconcile schedules: drop timezones that disappeared, add ones that only appear in refreshed data.
                        cancelStaleTimezones()
                        scheduleNewTimezones()
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            logger.error("Couldn't load the birthday calendar.", exception)
        } catch (exception: NullPointerException) {
            logger.error(null, exception)
        } catch (exception: ParserException) {
            logger.error("Couldn't parse the birthday calendar data.", exception)
        }
    }

    fun parseBirthdayDate(date: String): LocalDate? {
        if (date.length != 8) return null

        val year = (date.take(4))
        val month = (date.substring(4, 6))
        val day = (date.substring(6, 8))

        return try {
            LocalDate.parse("$year-$month-$day")
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Parse a timezone string and return the corresponding DateTimeZone.
     * Handles IANA timezone names (e.g., "Europe/Berlin") and UTC offsets (+0530, +05:30, -08:00).
     */
    fun parseTimeZone(tzString: String): TimeZone? {
        val cleaned = tzString.trim()

        if (cleaned.isEmpty()) return null

        // kotlinx only accepts offsets with a colon, so normalize +0530 to +05:30.
        val normalized = if (
            cleaned.length == 5 &&
                (cleaned[0] == '+' || cleaned[0] == '-') &&
                cleaned.substring(1).all { it.isDigit() }
        ) {
            "${cleaned.take(3)}:${cleaned.drop(3)}"
        } else {
            cleaned
        }

        return try {
            TimeZone.of(normalized)
        } catch (_: Exception) {
            null
        }
    }

    class Birthday(
        val eventName: String,
        val date: LocalDate,
        val userId: Long,
        val birthYear: Int? = null,
        val timezone: String? = null,
        val recurrenceSet: Set<Period<java.time.LocalDate>> = emptySet()
    ) {
        val username: String = if (eventName.endsWith(" | Birthday")) {
            eventName.dropLast(" | Birthday".length)
        } else {
            eventName
        }

        fun isToday(today: LocalDateTime): Boolean {
            if (recurrenceSet.isEmpty()) {
                return date.day == today.day && date.month == today.month
            }

            return recurrenceSet.any { it.includes(today.toJavaLocalDateTime()) }
        }

        companion object {
            fun fromComponent(component: Component): Birthday? {
                val properties = component.propertyList.all

                val name = properties.firstOrNull { it.name == "SUMMARY" }?.value
                    ?: return null

                val date = properties.firstOrNull { it.name == "DTSTART" }?.value?.let { parseBirthdayDate(it) }
                    ?: return null

                val userId = properties.firstOrNull { it.name == "LOCATION" }?.value?.toLongOrNull()
                    ?: return null

                val description = properties.firstOrNull { it.name == "DESCRIPTION" }?.value?.split("\n")

                // Line 1 = birth year, line 2 = timezone (optional)
                val year = description?.firstOrNull()?.trim()?.toIntOrNull()

                // Keep the timezone only when it actually parses, so unparseable values fall back to the server-time schedule.
                val timezone = description?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.takeIf { parseTimeZone(it) != null }

                val now = java.time.LocalDate.now()

                val recurrenceSet = component.calculateRecurrenceSet<java.time.LocalDate>(
                    Period(
                        now.minusYears(1),
                        now.plusYears(2)
                    )
                )

                return Birthday(name, date, userId, year, timezone, recurrenceSet)
            }
        }

        override fun toString(): String {
            return "Birthday(eventName='$eventName', date=$date, userId=$userId, birthYear=$birthYear, timezone='$timezone', username='$username')"
        }
    }
}