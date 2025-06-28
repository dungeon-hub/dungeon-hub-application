package net.dungeonhub.application.connection

import com.squareup.moshi.adapter
import net.dungeonhub.application.config.ConfigProperty
import net.dungeonhub.application.enums.FlaggingApi
import net.dungeonhub.application.enums.FlaggingApi.HypixelSafetyDataContainer
import net.dungeonhub.application.enums.FlaggingApi.HypixelSafetyDataContainer.HypixelSafetyData.HypixelSafetyDetail
import net.dungeonhub.application.misc.FlagDetail
import net.dungeonhub.application.misc.FlagDetail.FlagDetailBuilder.builder
import net.dungeonhub.application.misc.FlagResponse
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

    private var lastBlockGameRefresh: Instant? = null
    private var blockGameData: List<FlaggingApi.BlockGameData>? = null

    fun isFlagged(id: Long?): List<FlagResponse> {
        return isFlagged(null, id)
    }

    fun isFlagged(uuid: UUID?): List<FlagResponse> {
        return isFlagged(uuid, null)
    }

    fun isFlagged(uuid: UUID?, id: Long?): List<FlagResponse> {
        return FlaggingApi.entries.stream()
            .parallel()
            .map { flaggingApi: FlaggingApi -> flaggingApi.execute(uuid, id) }
            .toList()
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
        val httpUrl = (ConfigProperty.SAFETY_API_URL.toString() + "v1/user").toHttpUrl()
            .newBuilder()
            .addQueryParameter("user", uuid.toString())
            .addQueryParameter("type", "uuid")
            .build()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", ConfigProperty.SAFETY_API_KEY.value!!)
            .get()
            .build()

        return executeRequest(request, FlagDetail.Builder().flagged(false).build()) {
            fromSafetyResponse(
                moshi.adapter<HypixelSafetyDataContainer>().fromJson(
                    it
                )!!
            )
        }
    }

    fun isSafetyFlagged(id: Long): FlagDetail? {
        val httpUrl: HttpUrl = (ConfigProperty.SAFETY_API_URL.toString() + "v1/user").toHttpUrl()
            .newBuilder()
            .addQueryParameter("user", id.toString())
            .addQueryParameter("type", "discord")
            .build()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", ConfigProperty.SAFETY_API_KEY.value!!)
            .get()
            .build()

        return executeRequest(request, FlagDetail.Builder().flagged(false).build()) {
            fromSafetyResponse(
                moshi.adapter<HypixelSafetyDataContainer>().fromJson(
                    it
                )!!
            )
        }
    }

    fun fromSafetyResponse(hypixelSafetyDataContainer: HypixelSafetyDataContainer): FlagDetail {
        val data = hypixelSafetyDataContainer.data

        val flagged = data.ratter != null || data.scammer != null

        val builder = builder().flagged(flagged)

        var detail: HypixelSafetyDetail? = null
        if (data.ratter != null) {
            detail = data.ratter
        } else if (data.scammer != null) {
            detail = data.scammer
        }

        if (detail != null) {
            builder.reason(detail.reason)
            if (detail.evidence != null) {
                val evidences: MutableList<String> = ArrayList()
                detail.evidence!!.forEach {
                    evidences.add(it)
                }

                builder.evidence(java.lang.String.join(", ", evidences))
            }

            if (detail.moderator != null) {
                try {
                    builder.staff(detail.moderator!!.toLong())
                } catch (ignored: NumberFormatException) {
                    //ignored since this basically only applies if the id isn't a number, meaning this shouldn't be set
                }
            }
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
                } catch (ignored: NumberFormatException) {
                    //ignored since this basically only applies if the id is redacted, meaning this shouldn't be set
                }
            }

            if (detailObject.evidence != null && !detailObject.evidence.equals("<redacted>", ignoreCase = true)) {
                builder.evidence(detailObject.evidence)
            }
        }

        return builder.build()
    }
}