package me.taubsie.dungeonhub.application.service

import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartPriority
import me.taubsie.dungeonhub.application.loader.StartupListener
import net.dungeonhub.connection.DungeonHubConnection.apiUrl
import net.dungeonhub.connection.DungeonHubConnection.authLoginUrl
import net.dungeonhub.connection.DungeonHubConnection.cdnUrl
import net.dungeonhub.connection.DungeonHubConnection.clientId
import net.dungeonhub.connection.DungeonHubConnection.clientSecret
import net.dungeonhub.connection.DungeonHubConnection.staticUrl
import net.dungeonhub.hypixel.connection.HypixelConnection
import net.dungeonhub.hypixel.provider.CacheApiClientProvider

@OnStart(priority = StartPriority.PRE_BOT)
class ConfigurationService : StartupListener {
    override suspend fun preStart() {
        ConfigProperty.API_URL.value?.let { apiUrl = it }
        ConfigProperty.CDN_URL.value?.let { cdnUrl = it }
        ConfigProperty.STATIC_URL.value?.let { staticUrl = it }

        ConfigProperty.AUTH_LOGIN_URL.value?.let { authLoginUrl = it }
        ConfigProperty.AUTH_CLIENT_ID.value?.let { clientId = it }
        ConfigProperty.AUTH_CLIENT_SECRET.value?.let { clientSecret = it }

        ConfigProperty.HYPIXEL_API_KEY.value?.let { HypixelConnection.apiKey = it }
        CacheApiClientProvider.cacheTypeString = "Memory"
    }
}