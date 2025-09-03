package net.dungeonhub.application.enums

import com.squareup.moshi.Json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.dungeonhub.application.connection.FlaggingConnection
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.misc.FlagDetail
import net.dungeonhub.application.misc.FlagResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletionException
import java.util.function.Function

enum class FlaggingApi(
    val displayName: String,
    val uuidFunction: Function<UUID, FlagDetail?>?,
    val discordIdFunction: Function<Long, FlagDetail?>?
) {
    JERRY(
        "Jerry",
        { uuid: UUID ->
            FlaggingConnection.isJerryFlagged(uuid)
        },
        { discordId: Long ->
            FlaggingConnection.isJerryFlagged(discordId)
        }
    ),
    HYPIXEL_SAFETY(
        "Hypixel Safety",
        { uuid: UUID ->
            FlaggingConnection.isSafetyFlagged(uuid)
        },
        { discordId: Long ->
            FlaggingConnection.isSafetyFlagged(discordId)
        }
    ),
    BLOCK_GAME(
        "Scammer List (BlockHelper)",
        null,
        { discordId: Long ->
            FlaggingConnection.isBlockGameFlagged(discordId)
        }
    );

    class JerryData(val success: Boolean, val scammer: Boolean, val details: JerryDetails?) {
        class JerryDetails(
            val reason: String?,
            val staff: String?,
            val evidence: String?
        )
    }

    class HypixelSafetyData(
        @Json(name = "discord_id")
        val discordId: Long?,
        val uuid: String?, // isn't in dot notation, smh
        val username: String?,
        val scammer: Boolean,
        @Json(name = "scammer_data")
        val scammerData: HypixelSafetyDetail?,
        val ratter: Boolean,
        @Json(name = "ratter_data")
        val ratterData: HypixelSafetyDetail?
    ) {
        class HypixelSafetyDetail(
            val reason: String
        )
    }

    class BlockGameResponse(val success: Boolean, val data: BlockGameData)

    class BlockGameData(val results: Map<String, BlockGameResult?>)

    class BlockGameResult(val tag: String, val id: String, val method: String, val scammed: String)

    private val logger: Logger = LoggerFactory.getLogger(FlaggingApi::class.java)

    suspend fun execute(uuid: UUID?, id: Long?): FlagResponse = coroutineScope {
        try {
            val uuidFlagged = if (uuidFunction != null && uuid != null) {
                async { uuidFunction.apply(uuid) }
            } else {
                null
            }

            val discordIdFlagged = if (discordIdFunction != null && id != null && id != 0L) {
                async { discordIdFunction.apply(id) }
            } else {
                null
            }

            FlagResponse(
                displayName,
                uuid != null && uuidFlagged != null,
                uuidFlagged?.await(),
                id != null && discordIdFlagged != null,
                discordIdFlagged?.await(),
            )
        } catch (completionException: CompletionException) {
            logger.error(null, completionException)

            throw CommandExecutionException("Couldn't load scammer data.")
        }
    }
}