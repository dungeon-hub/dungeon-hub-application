package me.taubsie.dungeonhub.application.enums;

import lombok.Getter;
import me.taubsie.dungeonhub.common.Nameable;

//TODO add more
//TODO implement in carry tier / carry difficulty thumbnail settings ?
public enum KnownStaticResource implements Nameable {
    ICON_GIF("favicon.gif", "Icon (GIF)"),
    ICON("favicon.ico", "Icon"),

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

    @Getter
    final String path;
    final String displayName;

    KnownStaticResource(String path, String displayName) {
        this.path = path;
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return path;
    }

    @Override
    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }

        return getName();
    }
}