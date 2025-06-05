package net.dungeonhub.application.enums

import java.awt.Color

enum class EmbedColor(internalColor: Color) {
    Positive(Color(0, 255, 0)),
    Negative(Color(255, 0, 0)),
    Information(Color(255, 255, 255)),
    Default(Color(165, 23, 112));

    val color: dev.kord.common.Color = dev.kord.common.Color(internalColor.red, internalColor.green, internalColor.blue)
}