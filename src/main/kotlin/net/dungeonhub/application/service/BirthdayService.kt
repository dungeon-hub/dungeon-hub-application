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
import io.ktor.utils.io.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import java.util.HashSet

@OnStart
object BirthdayService : StartupListener {
    private const val BIRTHDAY_CALENDAR_URL =
        "https://cloud.dungeon-hub.net/remote.php/dav/public-calendars/g2tZRB2YpacJtAtt?export"
    private const val BIRTHDAY_PING_ROLE = 1362023553066860594
    private const val BIRTHDAYS_CHANNEL = 1362024376974839908
    private const val BIRTHDAY_CONGRATS_CHANNEL = 1082583379226148874
    private const val EXECUTION_HOUR = 9
    private val logger = LoggerFactory.getLogger(BirthdayService::class.java)
    private lateinit var scheduler: Scheduler
    var birthdays: List<Birthday> = listOf()
    private val scheduledTimezones = HashSet<String>()

    override suspend fun postStart() {
        if (::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduledTimezones.clear()
        scheduler = DhScheduler()

        updateBirthdayData()

        scheduleServerTimeBirthdays()

        // One schedule per distinct timezone, so each fires at 9am in that timezone.
        for (timezone in birthdays.mapNotNull { it.timezone }.distinct()) {
            scheduleTimezoneBirthdays(timezone)
        }
    }

    private suspend fun scheduleServerTimeBirthdays() {
        val task = scheduler.schedule(24.hours, startNow = false, name = "Birthdays-Schedule", repeat = true) {
            updateBirthdayData()

            sendBirthdays(TimeZone.currentSystemDefault(), null)
        }

        scheduler.launch {
            delay(calculateExecutionTime(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time))

            task.callNow()
            task.start()
        }
    }

    private suspend fun scheduleTimezoneBirthdays(timezone: String) {
        if (!scheduledTimezones.add(timezone)) return

        val timeZone = parseTimeZone(timezone) ?: return

        val task = scheduler.schedule(24.hours, startNow = false, name = "Birthdays-Schedule ($timezone)", repeat = true) {
            updateBirthdayData()

            sendBirthdays(timeZone, timezone)
        }

        scheduler.launch {
            delay(calculateExecutionTime(Clock.System.now().toLocalDateTime(timeZone).time))

            task.callNow()
            task.start()
        }
    }

    fun calculateExecutionTime(localTime: LocalTime): Duration {
        return if (localTime.hour <= EXECUTION_HOUR) {
            val hDifference = EXECUTION_HOUR - localTime.hour
            val mDifference = 0 - localTime.minute
            val sDifference = 0 - localTime.second

            hDifference.hours + mDifference.minutes + sDifference.seconds
        } else {
            val hDifference = localTime.hour - EXECUTION_HOUR
            val mDifference = localTime.minute
            val sDifference = localTime.second

            val totalDifference = hDifference * 60 * 60 + mDifference * 60 + sDifference

            24.hours.minus(totalDifference.seconds)
        }
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
            embed.description =
                "Happy Birthday, ${birthday.username} (<@${birthday.userId}>)! \uD83C\uDF89 \uD83E\uDD73 ❤\uFE0F" +
                    if (birthday.birthYear != null) {
                        "\nToday, they are turning ${now.year - birthday.birthYear} years old!"
                    } else {
                        ""
                    } +
                    "\nTheir birthday is on ${birthday.date.day.toString().padStart(2, '0')}.${birthday.date.month.number.toString().padStart(2, '0')}." +
                    if (birthday.timezone != null) {
                        "\nAnnounced at 9am in ${birthday.timezone}."
                    } else {
                        ""
                    } +
                    "\nMake sure to congratulate them in <#$BIRTHDAY_CONGRATS_CHANNEL>!"

            embeds += embed
        }

        if (embeds.isNotEmpty()) {
            birthdayChannel.createMessage {
                this.content = "<@&$BIRTHDAY_PING_ROLE>"
                this.embeds = embeds
            }
        }
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

                        // Reconcile schedules: timezones that only appear in refreshed data need their own schedule.
                        for (timezone in birthdays.mapNotNull { it.timezone }.distinct()) {
                            scheduleTimezoneBirthdays(timezone)
                        }
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
                val timezone = description?.getOrNull(1)?.trim()?.takeIf { it?.isNotEmpty() == true }?.takeIf { parseTimeZone(it) != null }

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