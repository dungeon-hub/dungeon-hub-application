package me.taubsie.carrylogs.application.enums;

import java.util.Arrays;
import java.util.Optional;

public enum CarryTier {
    COMPLETION("Completion"),
    S("S"),
    S_PLUS("S+"),
    FLOOR_1("Floor 1"),
    FLOOR_2("Floor 2"),
    FLOOR_3("Floor 3"),
    FLOOR_4("Floor 4"),
    FLOOR_5("Floor 5"),
    FLOOR_6("Floor 6"),
    FLOOR_7("Floor 7"),
    TIER_2("Tier 2"),
    TIER_3("Tier 3"),
    TIER_4("Tier 4"),
    BASIC("Basic"),
    HOT("Hot"),
    BURNING("Burning"),
    FIERY("Fiery"),
    INFERNAL("Infernal");

    private final String stringRepresentation;

    CarryTier(String stringRepresentation) {
        this.stringRepresentation = stringRepresentation;
    }

    public String getStringRepresentation() {
        return stringRepresentation;
    }

    public static Optional<CarryTier> fromString(String carryTier) {
        return Arrays.stream(CarryTier.values())
                .filter(tier -> tier.getStringRepresentation().equalsIgnoreCase(carryTier))
                .findFirst();
    }
}