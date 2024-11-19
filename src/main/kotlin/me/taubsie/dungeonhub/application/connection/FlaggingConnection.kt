package me.taubsie.dungeonhub.application.connection

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection.getBody
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection.httpClient
import me.taubsie.dungeonhub.application.enums.FlaggingApi
import me.taubsie.dungeonhub.application.misc.FlagDetail
import me.taubsie.dungeonhub.application.misc.FlagDetail.FlagDetailBuilder.builder
import me.taubsie.dungeonhub.application.misc.FlagResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.Consumer

object FlaggingConnection : Connection {
    override val logger: Logger = LoggerFactory.getLogger(FlaggingConnection::class.java)

    private var lastBlockGameRefresh: Instant? = null
    private var blockGameData: List<JsonObject>? = null

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

        return blockGameData!!.firstOrNull { jsonObject ->
            jsonObject.has("id")
                    && jsonObject["id"].isJsonPrimitive
                    && jsonObject.getAsJsonPrimitive("id").asLong == id
        }?.let { blockGameData ->
            this.loadFlagDetailFromBlockGameData(
                blockGameData
            )
        } ?: FlagDetail.Builder().flagged(false).build()
    }

    private fun loadFlagDetailFromBlockGameData(blockGameData: JsonObject): FlagDetail {
        val reason = StringBuilder()

        val scammedAmount = blockGameData.getAsJsonPrimitive("scammed")
        val method = blockGameData.getAsJsonPrimitive("method")

        if (scammedAmount != null && scammedAmount.isString) {
            reason.append("(").append(scammedAmount.asString).append(")")

            if (method != null && method.isString) {
                reason.append(" -> ")
            }
        }

        if (method != null && method.isString) {
            reason.append(method.asString)
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

        val jsonArray = executeRequest(
            request
        ) { s: String? ->
            fromJson(
                s!!,
                JsonArray::class.java
            )
        }

        if (jsonArray == null) {
            return
        }

        blockGameData = jsonArray.asList().parallelStream()
            .map { obj: JsonElement -> obj.asJsonObject }
            .toList()
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

        try {
            httpClient.newCall(request).execute().use { response ->
                return if (response.isSuccessful) {
                    logger.debug("Executed Safety-UUID request to '{}' successfully.", request.url)

                    response.body?.let { responseBody: ResponseBody ->
                        fromJson(
                            try {
                                responseBody.string()
                            } catch (ioException: IOException) {
                                logger.error(
                                    null,
                                    ioException
                                )
                                null
                            }!!,
                            JsonObject::class.java
                        ).let { rootObject: JsonObject -> this.fromSafetyResponse(rootObject) }
                    }
                } else if (response.code == 404) {
                    logger.debug("Executed Safety-UUID to '{}' returned a 404.", request.url)

                    FlagDetail.Builder().flagged(false).build()
                } else {
                    val body = getBody(request)

                    logger.error(
                        "Safety-UUID to '{}' wasn't successful. Body:\n{}\nResponse: {}\n{}",
                        request.url,
                        body,
                        response.code,
                        if (response.body != null) response.body!!.string() else null
                    )

                    null
                }
            }
        } catch (_: IOException) {
            return null
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

        try {
            httpClient.newCall(request).execute().use { response ->
                return if (response.isSuccessful) {
                    logger.debug("Executed Safety-id request to '{}' successfully.", request.url)

                    response.body?.let { responseBody: ResponseBody ->
                        fromJson(
                            try {
                                responseBody.string()
                            } catch (ioException: IOException) {
                                logger.error(
                                    null,
                                    ioException
                                )
                                null
                            }!!,
                            JsonObject::class.java
                        ).let { rootObject: JsonObject -> this.fromSafetyResponse(rootObject) }
                    }
                } else if (response.code == 404) {
                    logger.debug("Executed Safety-id request to '{}' returned a 404.", request.url)

                    FlagDetail.Builder().flagged(false).build()
                } else {
                    val body = getBody(request)

                    logger.error(
                        "Safety-id request to '{}' wasn't successful. Body:\n{}\nResponse: {}\n{}",
                        request.url,
                        body,
                        response.code,
                        if (response.body != null) response.body!!.string() else null
                    )

                    null
                }
            }
        } catch (_: IOException) {
            return null
        }
    }

    fun fromSafetyResponse(rootObject: JsonObject): FlagDetail {
        val dataObject = rootObject.getAsJsonObject("data")

        val flagged = dataObject.has("ratter") || dataObject.has("scammer")

        val builder = builder()
            .flagged(flagged)

        var detailObject: JsonObject? = null
        if (dataObject.has("ratter")) {
            detailObject = dataObject.getAsJsonObject("ratter")
        } else if (dataObject.has("scammer")) {
            detailObject = dataObject.getAsJsonObject("scammer")
        }

        if (detailObject != null) {
            builder.reason(detailObject.getAsJsonPrimitive("reason").asString)

            if (detailObject.has("evidence")) {
                val evidences: MutableList<String> = ArrayList()
                detailObject.getAsJsonArray("evidence")
                    .forEach(Consumer { jsonElement: JsonElement -> evidences.add(jsonElement.asString) })

                builder.evidence(java.lang.String.join(", ", evidences))
            }

            if (detailObject.has("moderator")) {
                try {
                    builder.staff(detailObject.getAsJsonPrimitive("moderator").asLong)
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
            fromJson(
                s!!,
                JsonObject::class.java
            )
        }?.takeIf { it.get("success").asBoolean }
            ?.let { rootObject: JsonObject -> this.fromJerryResponse(rootObject) }
    }

    fun isJerryFlagged(id: Long): FlagDetail? {
        val httpUrl = (ConfigProperty.JERRY_API_URL.toString() + "v1/scammers/discord/" + id).toHttpUrl()

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Authorization", "Bearer " + ConfigProperty.JERRY_API_KEY)
            .get()
            .build()

        return executeRequest(request) { s: String? ->
            fromJson(
                s!!,
                JsonObject::class.java
            )
        }?.takeIf { jsonObject -> jsonObject.get("success").asBoolean }
            ?.let { rootObject: JsonObject -> this.fromJerryResponse(rootObject) }
    }

    fun fromJerryResponse(rootObject: JsonObject): FlagDetail {
        val builder = builder()
            .flagged(rootObject["scammer"].asBoolean)

        if (rootObject.has("details") && rootObject["details"].isJsonObject) {
            val detailObject = rootObject.getAsJsonObject("details")

            if (detailObject.has("reason")) {
                builder.reason(detailObject.getAsJsonPrimitive("reason").asString)
            }

            if (detailObject.has("staff")) {
                try {
                    builder.staff(detailObject.getAsJsonPrimitive("staff").asLong)
                } catch (ignored: NumberFormatException) {
                    //ignored since this basically only applies if the id is redacted, meaning this shouldn't be set
                }
            }

            if (detailObject.has("evidence")) {
                val evidence = detailObject.getAsJsonPrimitive("evidence").asString
                if (!evidence.equals("<redacted>", ignoreCase = true)) {
                    builder.evidence(evidence)
                }
            }
        }

        return builder.build()
    }
}