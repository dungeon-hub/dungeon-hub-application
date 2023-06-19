package me.taubsie.carrylogs.application.enums;

import lombok.Getter;
import org.javacord.api.interaction.SlashCommandOptionChoice;

import java.util.Arrays;
import java.util.List;

//this relates to the new CarryTier
public enum CarryType {
    F4("F4", "Floor 4: Thorn", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"))),
    F5("F5", "Floor 5: Livid", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F6("F6", "Floor 6: Sadan", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F7("F7", "Floor 7: The Wither Lords", Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    MASTER_MODE("MM", "Master Mode", Arrays.asList(SlashCommandOptionChoice.create("Floor 1", "Floor 1"),
            SlashCommandOptionChoice.create("Floor 2", "Floor 2"),
            SlashCommandOptionChoice.create("Floor 3", "Floor 3"),
            SlashCommandOptionChoice.create("Floor 4", "Floor 4"),
            SlashCommandOptionChoice.create("Floor 5", "Floor 5"),
            SlashCommandOptionChoice.create("Floor 6", "Floor 6"),
            SlashCommandOptionChoice.create("Floor 7", "Floor 7"))),
    EMAN("Enderman", "Voidgloom Seraph", Arrays.asList(SlashCommandOptionChoice.create("Tier 3", "Tier 3"),
            SlashCommandOptionChoice.create("Tier 4", "Tier 4"))),
    BLAZE("Blaze", "Inferno Demonlord", Arrays.asList(SlashCommandOptionChoice.create("Tier 2", "Tier 2"),
            SlashCommandOptionChoice.create("Tier 3", "Tier 3"),
            SlashCommandOptionChoice.create("Tier 4", "Tier 4"))),
    KUUDRA("Kuudra", "Kuudra", Arrays.asList(SlashCommandOptionChoice.create("Basic", "Basic"),
            SlashCommandOptionChoice.create("Hot", "Hot"),
            SlashCommandOptionChoice.create("Burning", "Burning"),
            SlashCommandOptionChoice.create("Fiery", "Fiery"),
            SlashCommandOptionChoice.create("Infernal", "Infernal")));

    @Getter
    private final String prettyName;
    @Getter
    private final String descriptiveName;
    @Getter
    private final List<SlashCommandOptionChoice> choiceList;

    CarryType(String prettyName, String descriptiveName, List<SlashCommandOptionChoice> choiceList) {
        this.prettyName = prettyName;
        this.descriptiveName = descriptiveName;
        this.choiceList = choiceList;
    }

    public static CarryType fromString(String value) {
        try {
            return CarryType.valueOf(value);
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }
}