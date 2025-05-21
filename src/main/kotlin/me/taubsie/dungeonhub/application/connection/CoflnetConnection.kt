package me.taubsie.dungeonhub.application.connection

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import net.dungeonhub.hypixel.entities.skyblock.CurrentMember
import net.dungeonhub.provider.GsonProvider
import net.dungeonhub.provider.getAsJsonObjectOrNull
import net.dungeonhub.provider.getAsJsonPrimitiveOrNull
import net.dungeonhub.structure.ExternalConnection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

object CoflnetConnection : ExternalConnection {
    private val memberFields = listOf("rift", "pets_data", "currencies", "player_id", "inventory", "shared_inventory")
    override val logger = LoggerFactory.getLogger(CoflnetConnection::class.java)

    fun getNetworth(profileMember: CurrentMember): Long? {
        val url: HttpUrl = "https://sky.coflnet.com/api/networth".toHttpUrl()

        val requestBody: RequestBody = toJson(profileMember).toRequestBody(jsonMediaType)

        val request: Request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val result = try {
            //TODO maybe replace gson?
            executeRequest(request) { json -> GsonProvider.gson.fromJson(json, JsonObject::class.java) }
        } catch (ignored: JsonSyntaxException) {
            null
        }

        if (result == null) {
            return null
        }

        return result.getAsJsonPrimitiveOrNull("fullValue")?.asLong ?: 0L
    }

    fun toJson(profileMember: CurrentMember): String {
        val fields =
            //TODO remove this getAsJsonObject once the library updates -> raw data was incorrect
            profileMember.raw.getAsJsonObjectOrNull(profileMember.uuid.toString().replace("-", ""))!!.entrySet()
                .filter { memberFields.contains(it.key) }

        val memberData = jsonObject(fields.associate { it -> it.key to it.value })

        val memberObject = jsonObject(mapOf(profileMember.uuid.toString().replace("-", "") to memberData))

        //TODO maybe replace gson?
        return GsonProvider.gson.toJson(jsonObject(mapOf("members" to memberObject)))
    }

    private fun jsonObject(entries: Map<String, JsonElement>): JsonObject {
        val jsonObject = JsonObject()
        entries.forEach { (key, value) -> jsonObject.add(key, value) }
        return jsonObject
    }
}