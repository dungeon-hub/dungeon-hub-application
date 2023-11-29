package me.taubsie.dungeonhub.application.enums;

import me.taubsie.dungeonhub.common.Nameable;

//TODO add more
//TODO implement in carry tier / carry difficulty thumbnail settings ?
public enum KnownStaticResource implements Nameable {
    ICON_GIF("favicon.gif", "Icon (GIF)"),
    ICON("favicon.ico", "Icon"),

    BLAZE("blaze.png", "Blaze"),
    ENDERMAN("enderman.png", "Enderman"),
    MAGMA_CUBE("magma_cube.png", "Magma cube"),

    BONZO("bonzo.png", "Bonzo"),
    SCARF("scarf.png", "Scarf"),
    PROFESSOR("professor.png", "Professor"),

    RED_SKULL("red_skull.png", "Redstone skull");

    final String path;
    final String displayName;

    KnownStaticResource(String path, String displayName) {
        this.path = path;
        this.displayName = displayName;
    }

    public String getPath() {
        return "static/" + path;
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