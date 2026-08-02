package net.dungeonhub.application.service

import com.squareup.moshi.JsonDataException
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.misc.EmbedModel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbedJsonServiceTest {
    @Test
    fun `deserializes single embed object`() {
        val embeds = EmbedJsonService.parseEmbeds(
            """
            {
              "title": "Object title",
              "description": "Object description",
              "author": "Author name",
              "fields": [
                {"name": "Field name", "value": "Field value", "inline": true}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, embeds.size)
        assertEquals("Object title", embeds[0].title)
        assertEquals("Object description", embeds[0].description)
        assertEquals("Author name", embeds[0].author?.name)
        assertEquals("Field name", embeds[0].fields.single().name)
        assertEquals("Field value", embeds[0].fields.single().value)
        assertEquals(true, embeds[0].fields.single().inline)
    }

    @Test
    fun `deserializes embed array`() {
        val embeds = EmbedJsonService.parseEmbeds(
            """
            [
              {"title": "First", "description": "One"},
              {"title": "Second", "description": "Two"}
            ]
            """.trimIndent()
        )

        assertEquals(2, embeds.size)
        assertEquals("First", embeds[0].title)
        assertEquals("Two", embeds[1].description)
    }

    @Test
    fun `deserializes nested object variants`() {
        val embed = EmbedJsonService.parseEmbeds(
            """
            {
              "author": {"name": "Author", "url": "https://example.com/author", "icon": "https://example.com/author.png"},
              "footer": {"text": "Footer", "icon": "https://example.com/footer.png"},
              "thumbnail": {"url": "https://example.com/thumbnail.png"},
              "image": "https://example.com/image.png",
              "fields": [{"name": "Default field", "value": "Not inline"}]
            }
            """.trimIndent()
        ).single()

        assertEquals("Author", embed.author?.name)
        assertEquals("https://example.com/author", embed.author?.url)
        assertEquals("https://example.com/author.png", embed.author?.icon)
        assertEquals("Footer", embed.footer?.text)
        assertEquals("https://example.com/footer.png", embed.footer?.icon)
        assertEquals("https://example.com/thumbnail.png", embed.thumbnail?.url)
        assertEquals("https://example.com/image.png", embed.image)
        assertEquals(false, embed.fields.single().inline)
    }

    @Test
    fun `ignores unknown properties`() {
        val embed = EmbedJsonService.parseEmbeds(
            """{"title":"Known","unknown":{"nested":true}}"""
        ).single()

        assertEquals("Known", embed.title)
    }

    @Test
    fun `returns empty list for empty array and scalar input`() {
        assertTrue(EmbedJsonService.parseEmbeds("[]").isEmpty())
        assertTrue(EmbedJsonService.parseEmbeds("\"not an embed\"").isEmpty())
        assertTrue(EmbedJsonService.parseEmbeds("null").isEmpty())
    }

    @Test
    fun `rejects malformed json`() {
        assertFailsWith<IOException> {
            EmbedJsonService.parseEmbeds("{\"title\":")
        }
    }

    @Test
    fun `serializes single embed model`() {
        val model = EmbedModel(
            "Serialized title",
            "Serialized description",
            null,
            null,
            null,
            null,
            null,
            null,
            EmbedModel.Author("Author", null, null)
        )
        model.fields.add(EmbedModel.Field("Field", false, "Value"))

        val json = EmbedJsonService.toJson(model)
        val parsed = EmbedJsonService.parseEmbeds(json).single()

        assertEquals("Serialized title", parsed.title)
        assertEquals("Serialized description", parsed.description)
        assertEquals("Author", parsed.author?.name)
        assertEquals("Field", parsed.fields.single().name)
        assertEquals(false, parsed.fields.single().inline)
        assertEquals("Value", parsed.fields.single().value)
    }

    @Test
    fun `serializes embed model array`() {
        val json = EmbedJsonService.toJson(
            listOf(
                EmbedModel("First", null, null, null, null, null, null, null, null),
                EmbedModel("Second", null, null, null, null, null, null, null, null)
            )
        )

        val parsed = EmbedJsonService.parseEmbeds(json)

        assertEquals(2, parsed.size)
        assertEquals("First", parsed[0].title)
        assertEquals("Second", parsed[1].title)
        assertNull(parsed[0].description)
    }

    @Test
    fun `converts model to json map`() {
        val jsonMap = EmbedJsonService.toJsonMap(
            EmbedModel("Mapped", "Description", null, null, null, null, null, null, null)
        )

        assertEquals("Mapped", jsonMap["title"])
        assertEquals("Description", jsonMap["description"])
    }

    @Test
    fun `serializes arbitrary json values`() {
        val json = EmbedJsonService.toJsonValue(
            mapOf("items" to listOf("first", "second"), "enabled" to true)
        )

        assertEquals("{\"items\":[\"first\",\"second\"],\"enabled\":true}", json)
    }

    @Test
    fun `parses raw string as custom embed`() = runBlocking {
        val embeds = EmbedJsonService.parseEmbeds("\"stats-overview\"") { type, customData ->
            EmbedBuilder().apply { title = "$type:$customData" }
        }

        assertEquals("stats-overview:null", embeds.single().title)
    }

    @Test
    fun `parses custom embed object with custom data`() = runBlocking {
        val embeds = EmbedJsonService.parseEmbeds(
            """{"customEmbed":"stats-overview","customData":"SKILL_AVERAGE,CATACOMBS"}"""
        ) { type, customData ->
            EmbedBuilder().apply {
                title = type
                description = customData
            }
        }

        assertEquals("stats-overview", embeds.single().title)
        assertEquals("SKILL_AVERAGE,CATACOMBS", embeds.single().description)
    }

    @Test
    fun `parses mixed regular and custom embed array`() = runBlocking {
        val embeds = EmbedJsonService.parseEmbeds(
            """[{"title":"Regular"},"price-overview",{"customEmbed":"carry-price","customData":"5"}]"""
        ) { type, customData ->
            EmbedBuilder().apply { title = listOfNotNull(type, customData).joinToString(":") }
        }

        assertEquals(listOf("Regular", "price-overview", "carry-price:5"), embeds.map { it.title })
    }

    @Test
    fun `omits unresolved custom embeds`() = runBlocking {
        val embeds = EmbedJsonService.parseEmbeds(
            """["unknown",{"title":"Regular"},{"customEmbed":"also-unknown"}]"""
        ) { _, _ -> null }

        assertEquals(1, embeds.size)
        assertEquals("Regular", embeds.single().title)
    }

    @Test
    fun `rejects non-string custom embed type`() = runBlocking {
        val exception = assertFailsWith<JsonDataException> {
            EmbedJsonService.parseEmbeds("""{"customEmbed":42}""") { _, _ -> EmbedBuilder() }
        }

        assertEquals("customEmbed must be a string", exception.message)
    }

    @Test
    fun `rejects blank custom embed type`() = runBlocking {
        val exception = assertFailsWith<JsonDataException> {
            EmbedJsonService.parseEmbeds("""{"customEmbed":"  "}""") { _, _ -> EmbedBuilder() }
        }

        assertEquals("customEmbed must not be blank", exception.message)
    }

    @Test
    fun `rejects non-string custom data`() = runBlocking {
        val exception = assertFailsWith<JsonDataException> {
            EmbedJsonService.parseEmbeds(
                """{"customEmbed":"stats-overview","customData":["SKILL_AVERAGE"]}"""
            ) { _, _ -> EmbedBuilder() }
        }

        assertEquals("customData must be a string", exception.message)
    }

    @Test
    fun `ignores unsupported values in custom embed arrays`() = runBlocking {
        val embeds = EmbedJsonService.parseEmbeds(
            """[false,42,null,{"title":"Valid"}]"""
        ) { _, _ -> EmbedBuilder() }

        assertEquals(1, embeds.size)
        assertEquals("Valid", embeds.single().title)
    }
}
