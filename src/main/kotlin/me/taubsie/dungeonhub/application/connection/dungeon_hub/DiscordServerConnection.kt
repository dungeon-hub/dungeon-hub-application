package me.taubsie.dungeonhub.application.connection.dungeon_hub

import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import java.time.Instant

fun DiscordServerConnection.getTotalAmountOfMoneySpent(
    serverId: Long,
    userId: Long? = null,
    carrierId: Long? = null,
    carryTypeId: Long? = null,
    carryTierId: Long? = null,
    since: Instant? = null
): Long {
    return getTotalAmountOfMoneySpentOrNull(serverId, userId, carrierId, carryTypeId, carryTierId, since)
        ?: throw CommandExecutionException("Couldn't load the total amount of money spent.")
}

fun DiscordServerConnection.getTotalAmountOfMoneySpentOrNull(
    serverId: Long,
    userId: Long? = null,
    carrierId: Long? = null,
    carryTypeId: Long? = null,
    carryTierId: Long? = null,
    since: Instant? = null
): Long? {
    return fetchTotalAmountOfMoneySpent(serverId, userId, carrierId, carryTypeId, carryTierId, since).orElse(null)
}

fun DiscordServerConnection.getCarryAmount(
    serverId: Long,
    since: Instant? = null
): Long {
    return getCarryAmountOrNull(serverId, since)
        ?: throw CommandExecutionException("Couldn't load the total amount of carries.")
}

fun DiscordServerConnection.getCarryAmountOrNull(
    serverId: Long,
    since: Instant? = null
): Long? {
    return fetchCarryAmount(serverId, since).orElse(null)
}