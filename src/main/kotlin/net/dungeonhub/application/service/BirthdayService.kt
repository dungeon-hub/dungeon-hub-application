package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.utils.scheduling.Scheduler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.DateTimeZone
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.client.DungeonHubClient
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Period
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

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

    override suspend fun postStart() {
        if (::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = DhScheduler()

        val task = scheduler.schedule(24.hours, startNow = false, name = "Birthdays-Schedule", repeat = true) {
            updateBirthdayData()

            sendBirthdays()
        }

        // Calculate initial execution time for server time birthdays (no timezone)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        var maxExecutionTime = calculateExecutionTime(now.time)
        
        // For timezone-aware birthdays, we need to find the furthest upcoming 9am in their timezone
        // Convert each timezone's birthday date to server time and calculate execution duration
        for (birthday in birthdays) {
            if (birthday.timezone != null) {
                try {
                    val parsedTz = parseTimeZone(birthday.timezone)
                    if (parsedTz != null) {
                        // Find the next occurrence of this birthday
                        val nowLocalDate = Clock.System.now().toLocalDate()
                        
                        var localNineAm: LocalDateTime? = null
                        for (i in 0..365) {
                            val candidateDate = if (birthday.date == nowLocalDate) nowLocalDate else nowLocalDate.plusYears(1, 1, i.toLong())
                            localNineAm = LocalDateTime.of(candidateDate, LocalTime.of(9, 0))
                            
                            // Check if this date matches a birthday occurrence
                            if (isBirthdayOnDate(birthday.date, candidateDate)) {
                                break
                            }
                        }
                        
                        localNineAm?.let { nineAm ->
                            val executionTime = calculateExecutionTimeWithSpecificTime(nineAm)
                            maxExecutionTime = maxOf(maxExecutionTime, executionTime)
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Could not parse timezone for scheduling: ${birthday.timezone}", e)
                }
            }
        }

        scheduler.launch {
            delay(maxExecutionTime)
            task.callNow()
            task.start()
        }
    }

    /**
     * Calculate execution time to reach 9am in the given timezone.
     */
    private fun calculateExecutionTimeWithSpecificTime(targetDateTime: LocalDateTime): Duration {
        val targetLocalTime = targetDateTime.toLocalDateTime(TimeZone.currentSystemDefault()).time
        return if (targetLocalTime.hour <= EXECUTION_HOUR) {
            val hDifference = EXECUTION_HOUR - targetLocalTime.hour
            val mDifference = 0 - targetLocalTime.minute
            val sDifference = 0 - targetLocalTime.second
            hDifference.hours + mDifference.minutes + sDifference.seconds
        } else {
            val hDifference = targetLocalTime.hour - EXECUTION_HOUR
            val mDifference = targetLocalTime.minute
            val sDifference = targetLocalTime.second
            val totalDifference = hDifference * 60 * 60 + mDifference * 60 + sDifference
            24.hours.minus(totalDifference.seconds)
        }
    }

    /**
     * Calculate execution time to reach 9am local time.
     */
    private fun calculateExecutionTime(localTime: LocalTime): Duration {
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
     * Check if a birthday date falls on a specific calendar date (accounting for year progression).
     */
    private fun isBirthdayOnDate(birthdayDate: LocalDate, targetDate: LocalDate): Boolean {
        // Get the day-of-year for both dates
        val birthdayDayOfYear = java.time.LocalDate.of(birthdayDate.year, birthdayDate.month.value, birthdayDate.dayOfMonth.value)
            .dayOfYear

        return try {
            val nextYearBirthday = java.time.LocalDate.of(birthdayDate.year + 1, birthdayDate.month.value, birthdayDate.dayOfMonth.value).dayOfYear
            targetDate.dayOfYear in birthdayDayOfYear..nextYearBirthday
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun sendBirthdays() {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayBirthdays = getTodayBirthdays(now)
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        for (birthday in todayBirthdays) {
            val embed = EmbedBuilder()
            embed.color(EmbedColor.Positive)
            embed.title = birthday.eventName
            
            // Include the birthday date in the announcement text
            val formattedDate = "${birthday.date.day}/${birthday.date.month}"
            
            var timeInfo = if (birthday.timezone != null) {
                "Birthday time: 9am in their timezone (${birthday.timezone})"
            } else {
                "Birthday time: 9am server time"
            }
            
            embed.description = """
                Happy Birthday, ${birthday.username} (<@${birthday.userId}>)! 🎉 🤳 ❤️
                Today, they are turning ${now.year - birthday.birthYear} years old!
                Born on: $formattedDate
                
                $timeInfo
                
                Make sure to congratulate them in <#$BIRTHDAY_CONGRATS_CHANNEL>!
            """.trimIndent()

            embeds += embed
        }

        if (embeds.isNotEmpty()) {
            DiscordConnection.bot.kordRef.getChannelOf<GuildMessageChannel>(Snowflake(BIRTHDAYS_CHANNEL))
                ?.createMessage {
                    this.content = "<@&$BIRTHDAY_PING_ROLE>"
                    this.embeds = embeds
                }
        }
    }

    fun getTodayBirthdays(today: LocalDateTime): List<Birthday> {
        return birthdays.groupBy { it.userId }.map { it.value.maxBy { birthday -> birthday.date.year } }
            .filter { it.isToday(today) }
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
                    }
                }
            }
        } catch (exception: IOException) {
            logger.error(null, exception)
        } catch (exception: NullPointerException) {
            logger.error(null, exception)
        } catch (exception: Exception) {
            logger.error(null, exception)
        }
    }

    fun parseBirthdayDate(date: String): LocalDate? {
        if (date.length != 8) return null

        val year = date.take(4).toIntOrNull() ?: return null
        val month = date.substring(4, 6).toIntOrNull() ?: return null
        val day = date.substring(6, 8).toIntOrNull() ?: return null

        return try {
            LocalDate(year.toLong(), month.toLong(), day.toLong())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Parse a timezone string and return the corresponding DateTimeZone.
     * Handles IANA timezone names (e.g., "Europe/Berlin") and UTC offsets (+0530, -08:00).
     */
    fun parseTimeZone(tzString: String): DateTimeZone? {
        val cleaned = tzString.trim()
        
        return try {
            // Try IANA timezone name first (e.g., "America/New_York")
            if (cleaned.contains("/")) {
                return DateTimeZone.of(cleaned)
            }
            
            // Try UTC offset format (+0530, +05:30, -0800, etc.)
            DateTimeZone.of(cleaned)
        } catch (e: Exception) {
            logger.debug("Could not parse timezone: $tzString", e)
            return null
        }
    }

    class Birthday(
        val eventName: String,
        val date: LocalDate,
        val userId: Long,
        val birthYear: Int? = null,
        val timezone: String? = null,  // Added timezone field for timezone support
        val recurrenceSet: Set<Period<java.time.LocalDate>>
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

                val description = properties.firstOrNull { it.name == "DESCRIPTION" }?.value?.split("\n") ?: emptyList()

                // Parse: line 1 = birth year, line 2 = timezone (optional)
                val yearStr = description.getOrNull(0)?.trim()
                val year = yearStr?.toIntOrNull()

                // Line 2 contains timezone if present (after the year line)
                val timezone = description.getOrNull(1)?.trim()

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
