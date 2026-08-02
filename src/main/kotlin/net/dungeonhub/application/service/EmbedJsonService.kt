package net.dungeonhub.application.service

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import dev.kord.rest.builder.message.EmbedBuilder
import net.dungeonhub.application.misc.EmbedModel
import net.dungeonhub.service.MoshiService
import java.awt.Color
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

/**
 * Parses the embed JSON accepted by the embed commands.
 *
 * Discord embeds can be provided as a single JSON object or as a JSON array of objects. Some nested
 * values also support short forms, for example an author can either be a string or an object.
 */
@OptIn(ExperimentalStdlibApi::class)
object EmbedJsonService {
    private val anyAdapter = MoshiService.moshi.adapter<Any>()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = MoshiService.moshi.adapter<Map<String, Any?>>(mapType)
    private val listType = Types.newParameterizedType(List::class.java, mapType)
    private val listAdapter = MoshiService.moshi.adapter<List<Map<String, Any?>>>(listType)
    private val colorAdapter = MoshiService.moshi.adapter(Color::class.java)
    private val instantAdapter = MoshiService.moshi.adapter(Instant::class.java)
    private val embedModelAdapter = MoshiService.moshi.adapter<EmbedModel>()
    private val embedModelListAdapter = MoshiService.moshi.adapter<List<EmbedModel>>()

    fun parseEmbeds(source: String): List<EmbedBuilder> {
        val value = anyAdapter.fromJson(source) ?: return emptyList()

        return when (value) {
            is Map<*, *> -> listOf(mapAdapter.fromJson(source)!!.toEmbedBuilder())
            is List<*> -> listAdapter.fromJson(source).orEmpty().map { it.toEmbedBuilder() }
            else -> emptyList()
        }
    }

    /**
     * Parses regular embeds as well as application-specific embeds represented by either a string
     * or an object containing `customEmbed` and optional `customData` properties.
     */
    suspend fun parseEmbeds(
        source: String,
        customEmbedBuilder: suspend (type: String, customData: String?) -> EmbedBuilder?
    ): List<EmbedBuilder> {
        val value = anyAdapter.fromJson(source) ?: return emptyList()

        return when (value) {
            is Map<*, *> -> listOfNotNull(value.toCustomOrRegularEmbed(customEmbedBuilder))
            is List<*> -> value.mapNotNull { element ->
                when (element) {
                    is Map<*, *> -> element.toCustomOrRegularEmbed(customEmbedBuilder)
                    is String -> customEmbedBuilder(element, null)
                    else -> null
                }
            }
            is String -> listOfNotNull(customEmbedBuilder(value, null))
            else -> emptyList()
        }
    }

    fun toJson(model: EmbedModel): String = embedModelAdapter.toJson(model)

    fun toJson(models: List<EmbedModel>): String = embedModelListAdapter.toJson(models)

    fun toJsonMap(model: EmbedModel): Map<String, Any?> {
        return mapAdapter.fromJson(toJson(model)).orEmpty()
    }

    fun toJsonValue(value: Any?): String = anyAdapter.toJson(value)

    private fun Map<String, Any?>.toEmbedBuilder(): EmbedBuilder {
        val embedBuilder = EmbedBuilder()
        forEach { (key, value) -> embedBuilder.applyJson(key, value) }
        return embedBuilder
    }

    private suspend fun Map<*, *>.toCustomOrRegularEmbed(
        customEmbedBuilder: suspend (type: String, customData: String?) -> EmbedBuilder?
    ): EmbedBuilder? {
        if (containsKey("customEmbed")) {
            val customEmbed = this["customEmbed"] as? String
                ?: throw JsonDataException("customEmbed must be a string")
            if (customEmbed.isBlank()) {
                throw JsonDataException("customEmbed must not be blank")
            }

            val customData = this["customData"]?.let {
                it as? String ?: throw JsonDataException("customData must be a string")
            }
            return customEmbedBuilder(customEmbed, customData)
        }

        return asStringMap()?.toEmbedBuilder()
    }

    @OptIn(ExperimentalTime::class)
    fun EmbedBuilder.applyJson(key: String, value: Any?) {
        when (key) {
            "title" -> title = value.asStringOrNull()
            "description" -> description = value.asStringOrNull()
            "author" -> setAuthor(value)
            "url" -> url = value.asStringOrNull()
            "color" -> color = value?.let { colorAdapter.fromJsonValue(it) }?.let {
                dev.kord.common.Color(it.red, it.green, it.blue)
            }
            "fields" -> setFields(value)
            "footer" -> setFooter(value)
            "timestamp" -> timestamp = value.asStringOrNull()?.let { instantAdapter.fromJson(it)?.toKotlinInstant() }
            "thumbnail" -> setThumbnail(value)
            "image" -> image = value.asStringOrNull()
        }
    }

    private fun EmbedBuilder.setFields(value: Any?) {
        val fields = value.asListOrEmpty()
        fields.mapNotNull { it.asMapOrNull() }.forEach { field ->
            val name = field["name"].asStringOrNull() ?: return@forEach
            val fieldValue = field["value"].asStringOrNull() ?: return@forEach
            field(name, field["inline"] as? Boolean ?: false) { fieldValue }
        }
    }

    private fun EmbedBuilder.setFooter(value: Any?) {
        val footerData = value.asMapOrNull() ?: return
        footer {
            text = footerData["text"].asStringOrNull().orEmpty()
            icon = footerData["icon"].asStringOrNull()
        }
    }

    private fun EmbedBuilder.setThumbnail(value: Any?) {
        val thumbnailUrl = value.asMapOrNull()?.get("url").asStringOrNull() ?: return
        thumbnail { url = thumbnailUrl }
    }

    private fun EmbedBuilder.setAuthor(value: Any?) {
        if (value is String) {
            author { name = value }
            return
        }

        val authorData = value.asMapOrNull() ?: return
        author {
            name = authorData["name"].asStringOrNull()
            url = authorData["url"].asStringOrNull()
            icon = authorData["icon"].asStringOrNull()
        }
    }

    private fun Any?.asStringOrNull(): String? = this as? String

    private fun Any?.asListOrEmpty(): List<*> = this as? List<*> ?: emptyList<Any>()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMapOrNull(): Map<String, Any?>? = this as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.asStringMap(): Map<String, Any?>? {
        return takeIf { keys.all { key -> key is String } } as? Map<String, Any?>
    }
}
