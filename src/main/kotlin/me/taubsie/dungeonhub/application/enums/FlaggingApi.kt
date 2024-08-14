package me.taubsie.dungeonhub.application.enums

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.FlaggingConnection
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.misc.FlagDetail
import me.taubsie.dungeonhub.application.misc.FlagResponse
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
        { uuid: UUID? ->
            FlaggingConnection.getInstance().isJerryFlagged(uuid)
                .orElse(null)
        },
        { discordId: Long? ->
            FlaggingConnection.getInstance().isJerryFlagged(discordId)
                .orElse(null)
        }
    ),
    HYPIXEL_SAFETY(
        "Hypixel Safety",
        { uuid: UUID? ->
            FlaggingConnection.getInstance().isSafetyFlagged(uuid)
                .orElse(null)
        },
        { discordId: Long? ->
            FlaggingConnection.getInstance().isSafetyFlagged(discordId)
                .orElse(null)
        }
    ),
    BLOCK_GAME(
        "Block Game Bot",
        { _ -> null },
        { discordId: Long? ->
            FlaggingConnection.getInstance().isBlockGameFlagged(discordId)
                .orElse(null)
        }
    );

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
                    name,
                    uuidFlagged?.await(),
                    discordIdFlagged?.await(),
                )
            }
        } catch (completionException: CompletionException) {
            logger.error(null, completionException)

            throw CommandExecutionException("Couldn't load scammer data.")
        }
    }
}