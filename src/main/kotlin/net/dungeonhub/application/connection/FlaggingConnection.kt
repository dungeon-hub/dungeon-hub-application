package net.dungeonhub.application.connection

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.dungeonhub.application.config.ConfigProperty
import net.dungeonhub.application.enums.FlaggingApi
import net.dungeonhub.application.enums.FlaggingApi.HypixelSafetyData
import net.dungeonhub.application.enums.FlaggingApi.HypixelSafetyData.HypixelSafetyDetail
import net.dungeonhub.application.misc.FlagDetail
import net.dungeonhub.application.misc.FlagDetail.FlagDetailBuilder.builder
import net.dungeonhub.application.misc.FlagResponse
import net.dungeonhub.client.DungeonHubClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object FlaggingConnection {
    val logger: Logger = LoggerFactory.getLogger(FlaggingConnection::class.java)
    val client = DungeonHubClient()

    suspend fun isFlagged(id: Long?): List<FlagResponse> {
        return isFlagged(null, id)
    }

    suspend fun isFlagged(uuid: UUID?): List<FlagResponse> {
        return isFlagged(uuid, null)
    }

    suspend fun isFlagged(uuid: UUID?, id: Long?): List<FlagResponse> = coroutineScope {
        return@coroutineScope FlaggingApi.entries.map { flaggingApi ->
            async { flaggingApi.execute(uuid, id) }
        }.awaitAll()
    }

    suspend fun isSafetyFlagged(uuid: UUID): FlagDetail? = try {
        client.executeRawRequest {
            url(Url("${ConfigProperty.SAFETY_API_URL}check/$uuid"))
            parameter("type", "uuid")
            header("Authorization", "Key ${ConfigProperty.SAFETY_API_KEY.value!!}")
        }?.body<HypixelSafetyData>()?.let {
            fromSafetyResponse(it)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logger.error("Couldn't check safety-flagged status for uuid $uuid", exception)
        null
    }

    suspend fun isSafetyFlagged(id: Long): FlagDetail? = try {
        client.executeRawRequest {
            url(Url("${ConfigProperty.SAFETY_API_URL}check/$id"))
            parameter("type", "discord")
            header("Authorization", "Key ${ConfigProperty.SAFETY_API_KEY.value!!}")
        }?.body<HypixelSafetyData>()?.let {
            fromSafetyResponse(it)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logger.error("Couldn't check safety-flagged status for id $id", exception)
        null
    }

    fun fromSafetyResponse(data: HypixelSafetyData): FlagDetail {
        val flagged = data.ratter || data.scammer

        val builder = builder().flagged(flagged)

        var detail: HypixelSafetyDetail? = null
        if (data.ratter && data.ratterData != null) {
            detail = data.ratterData
        } else if (data.scammer && data.scammerData != null) {
            detail = data.scammerData
        }

        if (detail != null) {
            builder.reason(detail.reason)
        }

        return builder.build()
    }

    suspend fun isJerryFlagged(uuid: UUID): FlagDetail? {
        return try {
            client.executeRawRequest {
                url(Url(ConfigProperty.JERRY_API_URL.toString() + "v1/scammers/$uuid"))
                header("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
            }?.body<FlaggingApi.JerryData>()?.takeIf { it.success }?.let {
                fromJerryResponse(it)
            }
        } catch (exception: Exception) {
            logger.error("Couldn't check jerry-flagged status for uuid $uuid", exception)
            null
        }
    }

    suspend fun isJerryFlagged(id: Long): FlagDetail? {
        return try {
            client.executeRawRequest {
                url(Url(ConfigProperty.JERRY_API_URL.toString() + "v1/scammers/discord/$id"))
                header("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
            }?.body<FlaggingApi.JerryData>()?.takeIf { it.success }?.let {
                fromJerryResponse(it)
            }
        } catch (exception: Exception) {
            logger.error("Couldn't check jerry-flagged status for id $id", exception)
            null
        }
    }

    fun fromJerryResponse(jerryData: FlaggingApi.JerryData): FlagDetail {
        val builder = builder().flagged(jerryData.scammer)

        if (jerryData.details != null) {
            val detailObject = jerryData.details

            if (detailObject.reason != null) {
                builder.reason(detailObject.reason)
            }

            if (detailObject.staff != null) {
                try {
                    builder.staff(detailObject.staff.toLong())
                } catch (_: NumberFormatException) {
                    // ignored since this basically only applies if the id is redacted, meaning this shouldn't be set
                }
            }

            if (detailObject.evidence != null && !detailObject.evidence.equals("<redacted>", ignoreCase = true)) {
                builder.evidence(detailObject.evidence)
            }
        }

        return builder.build()
    }
}