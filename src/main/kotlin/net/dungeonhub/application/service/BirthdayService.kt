package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.utils.scheduling.Scheduler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeZone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import kotlin.time.Duration
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


    
    /**
     * Calculate execution time to reach a specific local time.
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

    override suspend fun postStart() {
        if (::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = DhScheduler()

        val task = scheduler.schedule(24.hours, startNow = false, name = "Birthdays-Schedule", repeat = true) {
            updateBirthdayData()

            sendBirthdays()
        }

        // Calculate initial execution time for server time birthdays
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        var maxExecutionTime = calculateExecutionTime(now.time)
        
        // For timezone-aware birthdays, we need to schedule them at their local 9am
        // This requires converting to server time and then to duration delay
        for (birthday in birthdays) {
            birthday.timezone?.let { tz ->
                try {
                    val parsedTz = parseTimeZone(tz)
                    if (parsedTz != null) {
                        val nowInServerTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        // Convert 9am in the user's timezone to server time equivalent
                        val localNineAm = LocalDateTime.of(nowInServerTime.toLocalDate(), parsedTz.toLocalTime())
                        val executionTime = calculateExecutionTimeWithSpecificTime(localNineAm)
                        maxExecutionTime = maxOf(maxExecutionTime, executionTime)
                    }
                } catch (e: Exception) {
                    logger.debug("Could not parse timezone for scheduling: $tz", e)
                }
            }
        }

        scheduler.launch {
            delay(maxExecutionTime)
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
            val age = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year - birthday.birthYear
            
            var timeInfo = if (birthday.timezone != null) {
                "Birthday time: 9am in their timezone (${birthday.timezone})"
            } else {
                "Birthday time: 9am server time"
            }
            
            embed.description = """
                Happy Birthday, ${birthday.username} (<@${birthday.userId}>)! 🎉 🤳 ❤️
                Today, they are turning $age years old!
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

        val year = (date.take(4))
        val month = (date.substring(4, 6))
        val day = (date.substring(6, 8))

        return try {
            LocalDate.parse("$year-$month-$day")
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun parseTimeZone(tzString: String): TimeZone? {
        // Handle IANA timezone names (e.g., "Europe/Berlin", "America/New_York")
        // Also handle simple offsets like "+0530" or "-0800"
        return try {
            if (tzString.startsWith("Etc/GMT")) {
                // Etc/GMT+HH:MM format - note the sign is reversed in IANA naming
                val parts = tzString.split("/")
                val offsetPart = parts.lastOrNull() ?: ""
                
                // Etc/GMT+05:30 means UTC-05:30 (opposite sign!)
                if (offsetPart.startsWith("Etc/GMT")) {
                    val sign = if (tzString.contains("+") && !tzString.contains("-")) "negative" else "positive"
                    return TimeZone.parse(tzString.replaceFirst("Etc/GMT", ""))
                }
            }
            
            // Handle simple offset format like +0530 or -0800
            val cleaned = tzString.trim()
            if (cleaned.length >= 5 && cleaned.all { it in "0123456789+-:" }) {
                var tzStr = cleaned
                // Remove trailing colon for consistency
                if (tzStr.endsWith(":")) {
                    tzStr = tzStr.substring(0, tzStr.length - 1)
                }
                
                return TimeZone.parse(tzStr)
            }
            
            // Handle IANA timezone names directly
            if (tzString.contains("/") && !tzString.startsWith("+") && !tzString.startsWith("-")) {
                return TimeZone.of(tzString)
            }
            
            null
        } catch (e: IllegalArgumentException) {
            logger.debug("Could not parse timezone: $tzString", e)
            null
        }
    }

    class Birthday(
        val eventName: String,
        val date: LocalDate,
        val userId: Long,
        val birthYear: Int? = null,
        val timezone: String? = null,  // Added timezone field
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
