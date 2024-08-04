package me.taubsie.dungeonhub.application.connection.dungeon_hub

import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException

fun DiscordServerConnection.getTotalAmountOfMoneySpent(
    serverId: Long,
    userId: Long? = null,
    carrierId: Long? = null,
    carryTypeId: Long? = null,
    carryTierId: Long? = null
): Long {
    return fetchTotalAmountOfMoneySpent(serverId, userId, carrierId, carryTypeId, carryTierId)
        .orElseThrow { CommandExecutionException("Couldn't load the total amount of money spent.") }
}