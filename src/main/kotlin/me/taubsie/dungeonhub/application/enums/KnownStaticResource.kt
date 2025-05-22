package me.taubsie.dungeonhub.application.enums

import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key
import lombok.Getter

//TODO implement in carry tier / carry difficulty thumbnail settings ?
enum class KnownStaticResource(@field:Getter val path: String, val displayName: String? = null) : ChoiceEnum {
    IconGif("favicon.gif", "Dungeon Hub Icon (GIF)"),
    Icon("favicon.ico", "Dungeon Hub Icon"),
    Banner("banner.png", "Dungeon Hub Banner"),

    Blaze("blaze.png"),
    Enderman("enderman.png"),
    MagmaCube("magma_cube.png", "Magma Cube"),
    Wither("wither.webp"),

    Bonzo("bonzo.png"),
    Scarf("scarf.png"),
    Professor("professor.png"),
    Thorn("thorn.png"),
    Livid("livid.png"),
    Sadan("sadan.png"),

    CatacombsEntrance("catacombs_entrance.png", "Catacombs Entrance"),
    CatacombsF1("catacombs_f1.png", "Catacombs Floor 1"),
    CatacombsF2("catacombs_f2.png", "Catacombs Floor 2"),
    CatacombsF3("catacombs_f3.png", "Catacombs Floor 3"),
    CatacombsF4("catacombs_f4.png", "Catacombs Floor 4"),
    CatacombsF5("catacombs_f5.png", "Catacombs Floor 5"),
    CatacombsF6("catacombs_f6.png", "Catacombs Floor 6"),
    CatacombsF7("catacombs_f7.png", "Catacombs Floor 7"),
    CatacombsM7("catacombs_m7.png", "Catacombs Master Mode 7"),

    GoldorsHelmet("helmet_goldor.png", "Goldor's Helmet"),
    MaxorHelmet("helmet_maxor.png", "Maxor's Helmet"),
    NecronsHelmet("helmet_necron.png", "Necron's Helmet"),
    StormHelmet("helmet_storm.png", "Storm's Helmet"),

    StrongArmor("armor_strong.png", "Strong Dragon Armor"),

    RedstoneKey("redstone_key.png", "Redstone Key"),

    VerificationExample("verification-example.mp4", "Verification Example");

    override val readableName: Key
        get() = loadDisplayName().toKey()

    fun loadDisplayName(): String {
        if (!displayName.isNullOrBlank()) {
            return displayName
        }

        return name
    }
}