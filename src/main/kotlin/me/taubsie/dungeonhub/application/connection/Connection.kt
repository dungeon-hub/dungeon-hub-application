package me.taubsie.dungeonhub.application.connection

import com.google.gson.Gson
import net.dungeonhub.service.GsonService
import net.dungeonhub.structure.MappingFunction
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import org.slf4j.Logger
import java.lang.reflect.Type

interface Connection {
    val logger: Logger?

    @Suppress("DEPRECATION")
    val gson: Gson
        get() = GsonService.gson

    fun <T> fromJson(json: String, clazz: Class<T>): T {
        return this.gson.fromJson(json, clazz)
    }

    fun <T> fromJson(json: String, typeOfT: Type): T {
        return this.gson.fromJson(json, typeOfT)
    }

    fun <T> toJson(entity: T): String {
        return this.gson.toJson(entity)
    }

    val jsonMediaType: MediaType?
        get() = "application/json; charset=utf-8".toMediaTypeOrNull()

    fun <T> executeRequest(request: Request, function: MappingFunction<String, T>): T? {
        return DungeonHubConnection.executeRequest(request, function)
    }

    fun executeRequest(request: Request): String? {
        return DungeonHubConnection.executeRequest(request)
    }
}