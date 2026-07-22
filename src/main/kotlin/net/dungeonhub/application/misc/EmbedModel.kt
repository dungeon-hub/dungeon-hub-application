package net.dungeonhub.application.misc

import dev.kord.rest.builder.message.EmbedBuilder.Footer
import dev.kord.rest.builder.message.EmbedBuilder.Thumbnail
import java.awt.Color
import java.time.Instant

class EmbedModel(
    var title: String?,
    var description: String?,
    var url: String?,
    var timestamp: Instant?,
    var color: Color?,
    var image: String?,
    var footer: Footer?,
    var thumbnail: Thumbnail?,
    var author: Author?
) {
    var fields: MutableList<Field> = mutableListOf()

    class Author(
        var name: String?,
        var url: String?,
        var icon: String?
    )

    class Field(
        var name: String,
        var inline: Boolean?,
        var value: String
    )
}