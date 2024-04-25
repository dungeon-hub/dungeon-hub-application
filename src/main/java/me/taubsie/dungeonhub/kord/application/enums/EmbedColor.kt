package me.taubsie.dungeonhub.kord.application.enums

import dev.kord.common.Color
import lombok.Getter

@Getter
enum class EmbedColor(val color: Color) {
    POSITIVE(Color(0, 255, 0)),
    NEGATIVE(Color(255, 0, 0)),
    INFORMATION(Color(255, 255, 255)),
    DEFAULT(Color(165, 23, 112))
}