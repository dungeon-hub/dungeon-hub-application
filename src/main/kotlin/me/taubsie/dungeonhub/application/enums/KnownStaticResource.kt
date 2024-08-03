package me.taubsie.dungeonhub.application.enums

import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import lombok.Getter

//TODO add more
//TODO implement in carry tier / carry difficulty thumbnail settings ?
enum class KnownStaticResource(@field:Getter val path: String, val displayName: String?): ChoiceEnum {
    ICON_GIF("favicon.gif", "Icon (GIF)"),
    ICON("favicon.ico", "Icon"),
    BANNER("banner.png", "Banner"),

    BLAZE("blaze.png", "Blaze"),
    ENDERMAN("enderman.png", "Enderman"),
    MAGMA_CUBE("magma_cube.png", "Magma cube"),
    WITHER("wither.webp", "Wither"),

    BONZO("bonzo.png", "Bonzo"),
    SCARF("scarf.png", "Scarf"),
    PROFESSOR("professor.png", "Professor"),
    THORN("thorn.png", "Thorn"),
    LIVID("livid.png", "Livid"),
    SADAN("sadan.png", "Sadan"),

    REDSTONE_KEY("redstone_key.png", "Redstone Key"),

    VERIFICATION_EXAMPLE("verification-example.mp4", "Verification Example");

    fun getName(): String {
        return path
    }

    override val readableName: String
        get() = loadDisplayName()

    fun loadDisplayName(): String {
        if (!displayName.isNullOrBlank()) {
            return displayName
        }

        return getName()
    }
}