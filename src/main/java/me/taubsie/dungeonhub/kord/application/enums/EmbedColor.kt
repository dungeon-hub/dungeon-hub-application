package me.taubsie.dungeonhub.kord.application.enums

import java.awt.Color

//TODO update names like Positive to better use kotlin's style
enum class EmbedColor(internalColor: Color) {
    POSITIVE(Color(0, 255, 0)),
    NEGATIVE(Color(255, 0, 0)),
    INFORMATION(Color(255, 255, 255)),
    DEFAULT(Color(165, 23, 112));

    val color: dev.kord.common.Color = dev.kord.common.Color(internalColor.red, internalColor.green, internalColor.blue)
}