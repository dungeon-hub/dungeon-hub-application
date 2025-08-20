package net.dungeonhub.application.connection

import com.squareup.moshi.adapter
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
import net.dungeonhub.service.MoshiService.moshi
import net.dungeonhub.structure.ExternalConnection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
object FlaggingConnection : ExternalConnection {
    override val logger: Logger = LoggerFactory.getLogger(FlaggingConnection::class.java)
    override val client = DungeonHubClient()

    private var lastBlockGameRefresh: Instant? = null
    private var blockGameData: List<FlaggingApi.BlockGameData>? = null

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

    fun isBlockGameFlagged(id: Long): FlagDetail {
        if (lastBlockGameRefresh == null || blockGameData == null || lastBlockGameRefresh!!.isBefore(
                Instant.now().minus(5, ChronoUnit.MINUTES)
            )
        ) {
            refreshBlockGameData()
        }

        return blockGameData!!.firstOrNull { data ->
            data.id == id
        }?.let { blockGameData ->
            this.loadFlagDetailFromBlockGameData(
                blockGameData
            )
        } ?: FlagDetail.Builder().flagged(false).build()
    }

    private fun loadFlagDetailFromBlockGameData(blockGameData: FlaggingApi.BlockGameData): FlagDetail {
        val reason = StringBuilder()

        if (blockGameData.scammed != null) {
            reason.append("(").append(blockGameData.scammed).append(")")

            if (blockGameData.method != null) {
                reason.append(" -> ").append(blockGameData.method)
            }
        }

        if (blockGameData.method != null) {
            reason.append(blockGameData.method)
        }

        return builder()
            .flagged(true)
            .reason(reason.toString())
            .build()
    }

    fun refreshBlockGameData() {
        lastBlockGameRefresh = Instant.now()

        val httpUrl: HttpUrl = "https://block.lenny.ie/scammers".toHttpUrl()

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .build()

        val jsonArray = executeRequest(request) { s -> moshi.adapter<List<FlaggingApi.BlockGameData>>().fromJson(s) }

        if (jsonArray == null) {
            return
        }

        blockGameData = jsonArray
    }

    fun isSafetyFlagged(uuid: UUID): FlagDetail? {
        val httpUrl = (ConfigProperty.SAFETY_API_URL.toString() + "check/" + uuid).toHttpUrl()
            .newBuilder()
            .addQueryParameter("type", "uuid")
            .build()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Key " + ConfigProperty.SAFETY_API_KEY.value!!)
            .get()
            .build()

        return executeRequest(request, FlagDetail.Builder().flagged(false).build()) {
            fromSafetyResponse(
                moshi.adapter<HypixelSafetyData>().fromJson(
                    it
                )!!
            )
        }
    }

    fun isSafetyFlagged(id: Long): FlagDetail? {
        val httpUrl: HttpUrl = (ConfigProperty.SAFETY_API_URL.toString() + "check/" + id).toHttpUrl()
            .newBuilder()
            .addQueryParameter("type", "discord")
            .build()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Key " + ConfigProperty.SAFETY_API_KEY.value!!)
            .get()
            .build()

        return executeRequest(request, FlagDetail.Builder().flagged(false).build()) {
            fromSafetyResponse(
                moshi.adapter<HypixelSafetyData>().fromJson(
                    it
                )!!
            )
        }
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

    fun isJerryFlagged(uuid: UUID): FlagDetail? {
        val httpUrl: HttpUrl = (ConfigProperty.JERRY_API_URL.toString() + "v1/scammers/" + uuid).toHttpUrl()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
            .get()
            .build()

        return executeRequest(request) { s: String? ->
            moshi.adapter<FlaggingApi.JerryData>().fromJson(
                s!!
            )
        }?.takeIf { it.success }
            ?.let { rootObject -> this.fromJerryResponse(rootObject) }
    }

    fun isJerryFlagged(id: Long): FlagDetail? {
        val httpUrl = (ConfigProperty.JERRY_API_URL.toString() + "v1/scammers/discord/" + id).toHttpUrl()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
            .get()
            .build()

        return executeRequest(request) { s: String? ->
            moshi.adapter<FlaggingApi.JerryData>().fromJson(
                s!!
            )
        }?.takeIf { jsonObject -> jsonObject.success }
            ?.let { rootObject -> this.fromJerryResponse(rootObject) }
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