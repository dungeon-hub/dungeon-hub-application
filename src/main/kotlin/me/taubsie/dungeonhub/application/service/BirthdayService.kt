package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartupListener
import net.dungeonhub.connection.DungeonHubConnection.httpClient
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Period
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@OnStart
object BirthdayService : StartupListener {
    private const val BIRTHDAY_CALENDAR_URL =
        "https://cloud.dungeon-hub.net/remote.php/dav/public-calendars/g2tZRB2YpacJtAtt?export"
    private const val BIRTHDAY_PING_ROLE = 1362023553066860594
    private const val BIRTHDAYS_CHANNEL = 1362024376974839908
    private const val BIRTHDAY_CONGRATS_CHANNEL = 1082583379226148874
    private const val EXECUTION_HOUR = 9
    private val logger = LoggerFactory.getLogger(BirthdayService::class.java)
    private var timerTask: ScheduledFuture<*>? = null
    var birthdays: List<Birthday> = listOf()

    override suspend fun postStart() {
        val timeUntilExecutionTime =
            calculateExecutionTime(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time)

        if (timerTask != null) {
            timerTask!!.cancel(false)
        }

        timerTask = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            updateBirthdayData()

            sendBirthdays()
        }, timeUntilExecutionTime, 60 * 60 * 24, TimeUnit.SECONDS)
    }

    fun calculateExecutionTime(localTime: LocalTime): Long {
        return if (localTime.hour <= EXECUTION_HOUR) {
            val hDifference = EXECUTION_HOUR - localTime.hour
            val mDifference = 0 - localTime.minute
            val sDifference = 0 - localTime.second

            hDifference * 60L * 60 + mDifference * 60 + sDifference
        } else {
            val hDifference = localTime.hour - EXECUTION_HOUR
            val mDifference = localTime.minute
            val sDifference = localTime.second

            val totalDifference = hDifference * 60 * 60 + mDifference * 60 + sDifference

            24L * 60 * 60 - totalDifference
        }
    }

    private fun sendBirthdays() {
        val todayBirthdays = getTodayBirthdays(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        for (birthday in todayBirthdays) {
            val embed = EmbedBuilder()
            embed.color(EmbedColor.Positive)
            embed.title = birthday.eventName
            embed.description =
                "Happy Birthday, ${birthday.username} (<@${birthday.userId}>)! \uD83C\uDF89 \uD83E\uDD73 ❤\uFE0F ${
                    if (birthday.birthYear != null) {
                        "\nToday, they are turning ${
                            Clock.System.now()
                                .toLocalDateTime(TimeZone.currentSystemDefault()).year - birthday.birthYear
                        } years old!"
                    } else {
                        ""
                    }
                }\nMake sure to congratulate them in <#$BIRTHDAY_CONGRATS_CHANNEL>!"

            embeds += embed
        }

        if (embeds.isNotEmpty()) {
            runBlocking {
                DiscordConnection.bot!!.kordRef.getChannelOf<GuildMessageChannel>(Snowflake(BIRTHDAYS_CHANNEL))
                    ?.createMessage {
                        this.content = "<@$BIRTHDAY_PING_ROLE>"
                        this.embeds = embeds
                    }
            }
        }
    }

    fun getTodayBirthdays(today: LocalDateTime): List<Birthday> {
        return birthdays.groupBy { it.userId }.map { it.value.maxBy { birthday -> birthday.date.year } }
            .filter { it.isToday(today) }
    }

    fun updateBirthdayData() {
        val request = Request.Builder()
            .url(BIRTHDAY_CALENDAR_URL)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().let { response ->
                if (response.isSuccessful && response.body != null) {
                    response.body!!.byteStream().let { inputStream: InputStream ->
                        CalendarBuilder().build(inputStream).let { calendar ->
                            val birthdayList = calendar.componentList.all.mapNotNull { component ->
                                Birthday.fromComponent(component)
                            }

                            if (birthdayList.isNotEmpty()) {
                                birthdays = birthdayList
                            }
                        }
                    }
                }
            }
        } catch (exception: IOException) {
            logger.error(null, exception)
        } catch (exception: NullPointerException) {
            logger.error(null, exception)
        }
    }

    fun parseBirthdayDate(date: String): LocalDate? {
        if (date.length != 8) return null

        val year = (date.substring(0, 4))
        val month = (date.substring(4, 6))
        val day = (date.substring(6, 8))

        return try {
            LocalDate.parse("$year-$month-$day")
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    class Birthday(
        val eventName: String,
        val date: LocalDate,
        val userId: Long,
        val birthYear: Int? = null,
        val recurrenceSet: Set<Period<java.time.LocalDate>>
    ) {
        val username: String = if (eventName.endsWith(" | Birthday")) {
            eventName.substring(0, eventName.length - 11)
        } else {
            eventName
        }

        fun isToday(today: LocalDateTime): Boolean {
            if (recurrenceSet.isEmpty()) {
                return date.dayOfMonth == today.dayOfMonth && date.month == today.month
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

                val year = description?.firstOrNull()?.trim()?.toIntOrNull()

                val now = java.time.LocalDate.now()

                val recurrenceSet = component.calculateRecurrenceSet<java.time.LocalDate>(
                    Period(
                        now.minusYears(1),
                        now.plusYears(2)
                    )
                )

                return Birthday(name, date, userId, year, recurrenceSet)
            }
        }

        override fun toString(): String {
            return "Birthday(eventName='$eventName', date=$date, userId=$userId, birthYear=$birthYear, username='$username')"
        }
    }
}