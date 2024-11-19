package me.taubsie.dungeonhub.application.connection

import com.google.gson.JsonParser
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundWarning
import net.dungeonhub.connection.DungeonHubConnection.httpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

object MojangConnection {
    private val logger: Logger = LoggerFactory.getLogger(MojangConnection::class.java)

    fun fromString(uuid: String): UUID {
        //TODO check for uuid format
        return UUID.fromString(
            uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)".toRegex(),
                "$1-$2-$3-$4-$5"
            )
        )
    }

    //As this requests data from the Mojang API (aka slow), it is recommended to use UUIDs instead of names
    @Throws(PlayerNotFoundWarning::class)
    fun getUUIDByName(name: String): UUID {
        val request = Request.Builder()
            .url("https://api.mojang.com/users/profiles/minecraft/$name")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body!= null) {
                    return fromString(JsonParser.parseString(response.body!!.string()).asJsonObject["id"].asString)
                }
            }
        } catch (exception: IOException) {
            logger.error(null, exception)
        } catch (exception: NullPointerException) {
            logger.error(null, exception)
        }

        throw PlayerNotFoundWarning(name)
    }

    @Throws(PlayerNotFoundWarning::class)
    fun getNameByUUID(uuid: UUID): String {
        val request = Request.Builder()
            .url("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    return JsonParser.parseString(response.body!!.string()).asJsonObject["name"].asString
                }
            }
        } catch (exception: IOException) {
            logger.error(null, exception)
        } catch (exception: NullPointerException) {
            logger.error(null, exception)
        }

        throw PlayerNotFoundWarning(uuid)
    }
}