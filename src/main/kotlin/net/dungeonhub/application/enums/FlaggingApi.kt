package net.dungeonhub.application.enums

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
        "Block Game Bot",
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

    class HypixelSafetyDataContainer(val data: HypixelSafetyData) {
        class HypixelSafetyData(val ratter: HypixelSafetyDetail?, val scammer: HypixelSafetyDetail?) {
            class HypixelSafetyDetail(
                val reason: String,
                val evidence: List<String>?,
                val moderator: String?
            )
        }
    }

    class BlockGameData(val id: Long, val scammed: String?, val method: String?)

    private val logger: Logger = LoggerFactory.getLogger(FlaggingApi::class.java)

    fun execute(uuid: UUID?, id: Long?): FlagResponse {
        try {
            return runBlocking {
                val uuidFlagged = if (uuidFunction != null && uuid != null) {
                    async {
                        uuidFunction.apply(uuid)
                    }
                } else {
                    null
                }

                val discordIdFlagged = if (discordIdFunction != null && id != null && id != 0L) {
                    async {
                        discordIdFunction.apply(id)
                    }
                } else {
                    null
                }

                return@runBlocking FlagResponse(
                    displayName,
                    uuid != null && uuidFlagged != null,
                    uuidFlagged?.await(),
                    id != null && discordIdFlagged != null,
                    discordIdFlagged?.await(),
                )
            }
        } catch (completionException: CompletionException) {
            logger.error(null, completionException)

            throw CommandExecutionException("Couldn't load scammer data.")
        }
    }
}