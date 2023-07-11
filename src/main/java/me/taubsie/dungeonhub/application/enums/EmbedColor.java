package me.taubsie.dungeonhub.application.enums;

import java.awt.*;

public enum EmbedColor {
    POSITIVE(new Color(0, 255, 0)),
    NEGATIVE(new Color(255, 0, 0)),
    INFORMATION(new Color(255, 255, 255)),
    DEFAULT(new Color(165, 23, 112));

    private final Color color;

    EmbedColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }
}