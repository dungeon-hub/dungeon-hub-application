package me.taubsie.carrylogs.application.enums;

import java.util.Arrays;
import java.util.Optional;

public enum CarryTier {
    COMPLETION("Completion"),
    S("S", "S Score"),
    S_PLUS("S+", "S+ Score"),
    FLOOR_1("Floor 1", "Master 1: Bonzo"),
    FLOOR_2("Floor 2", "Master 2: Scarf"),
    FLOOR_3("Floor 3", "Master 3: The Professor"),
    FLOOR_4("Floor 4", "Master 4: Thorn"),
    FLOOR_5("Floor 5", "Master 5: Livid"),
    FLOOR_6("Floor 6", "Master 6: Sadan"),
    FLOOR_7("Floor 7", "Master 7: The Wither Lords"),
    TIER_2("Tier 2"),
    TIER_3("Tier 3"),
    TIER_4("Tier 4"),
    BASIC("Basic"),
    HOT("Hot"),
    BURNING("Burning"),
    FIERY("Fiery"),
    INFERNAL("Infernal");

    private final String stringRepresentation;
    private final String descriptiveName;

    CarryTier(String stringRepresentation, String descriptiveName) {
        this.stringRepresentation = stringRepresentation;
        this.descriptiveName = descriptiveName;
    }

    CarryTier(String stringRepresentation) {
        this(stringRepresentation, stringRepresentation);
    }

    public String getStringRepresentation() {
        return stringRepresentation;
    }

    public String getDescriptiveName() {
        return descriptiveName;
    }

    public static Optional<CarryTier> fromString(String carryTier) {
        return Arrays.stream(CarryTier.values())
                .filter(tier -> tier.getStringRepresentation().equalsIgnoreCase(carryTier))
                .findFirst();
    }
}