package net.dungeonhub.application.service

import net.dungeonhub.application.config.ConfigProperty
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.auth.AuthenticationCredentials.authLoginUrl
import net.dungeonhub.auth.AuthenticationCredentials.clientId
import net.dungeonhub.auth.AuthenticationCredentials.clientSecret
import net.dungeonhub.cache.database.MongoCacheProvider
import net.dungeonhub.client.DungeonHubClient.Companion.apiUrl
import net.dungeonhub.client.DungeonHubClient.Companion.cdnUrl
import net.dungeonhub.client.DungeonHubClient.Companion.staticUrl
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
        ConfigProperty.HYPIXEL_CACHE_TYPE.value?.let { CacheApiClientProvider.cacheTypeString = it }

        ConfigProperty.MONGODB_CONNECTION_STRING.value?.let { MongoCacheProvider.connectionString = it }
        ConfigProperty.MONGODB_DATABASE_NAME.value?.let { MongoCacheProvider.databaseName = it }
        MongoCacheProvider.collectionPrefix = ""
    }
}