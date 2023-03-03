package me.taubsie.carrylogs.application.enums;

import lombok.Getter;
import org.javacord.api.interaction.SlashCommandOptionChoice;

import java.util.Arrays;
import java.util.List;

public enum CarryType
{
    F4("Floor 4", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"))),
    F5("Floor 5", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F6("Floor 6", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F7("Floor 7", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    MASTER_MODE("MM", Arrays.asList(SlashCommandOptionChoice.create("Floor 1", "Floor 1"),
            SlashCommandOptionChoice.create("Floor 2", "Floor 2"),
            SlashCommandOptionChoice.create("Floor 3", "Floor 3"),
            SlashCommandOptionChoice.create("Floor 4", "Floor 4"),
            SlashCommandOptionChoice.create("Floor 5", "Floor 5"),
            SlashCommandOptionChoice.create("Floor 6", "Floor 6"),
            SlashCommandOptionChoice.create("Floor 7", "Floor 7"))),
    EMAN("Enderman", Arrays.asList(SlashCommandOptionChoice.create("Tier 3", "Tier 3"),
            SlashCommandOptionChoice.create("Tier 4", "Tier 4"))),
    BLAZE("Blaze", Arrays.asList(SlashCommandOptionChoice.create("Tier 2", "Tier 2"),
            SlashCommandOptionChoice.create("Tier 3", "Tier 3"),
            SlashCommandOptionChoice.create("Tier 4", "Tier 4"))),
    KUUDRA("Kuudra", Arrays.asList(SlashCommandOptionChoice.create("Basic", "Basic"),
            SlashCommandOptionChoice.create("Hot", "Hot"),
            SlashCommandOptionChoice.create("Burning", "Burning"),
            SlashCommandOptionChoice.create("Fiery", "Fiery"),
            SlashCommandOptionChoice.create("Infernal", "Infernal")));

    @Getter
    private final String prettyName;
    @Getter
    private final List<SlashCommandOptionChoice> choiceList;

    CarryType(String prettyName, List<SlashCommandOptionChoice> choiceList) {
        this.prettyName = prettyName;
        this.choiceList = choiceList;
    }

    public static CarryType fromString(String value) {
        try {
            return CarryType.valueOf(value);
        } catch(IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }
}