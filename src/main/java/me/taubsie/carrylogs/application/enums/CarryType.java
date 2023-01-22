package me.taubsie.carrylogs.application.enums;

import lombok.Getter;
import org.javacord.api.interaction.SlashCommandOptionChoice;

import java.util.Arrays;
import java.util.List;

public enum CarryType
{
    F4(Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F5(Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F6(Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    F7(Arrays.asList(SlashCommandOptionChoice.create("Completion", "Completion"),
            SlashCommandOptionChoice.create("S", "S"),
            SlashCommandOptionChoice.create("S+", "S+"))),
    MASTER_MODE(Arrays.asList(SlashCommandOptionChoice.create("Floor 1", "Floor 1"),
            SlashCommandOptionChoice.create("Floor 2", "Floor 2"),
            SlashCommandOptionChoice.create("Floor 3", "Floor 3"),
            SlashCommandOptionChoice.create("Floor 4", "Floor 4"),
            SlashCommandOptionChoice.create("Floor 5", "Floor 5"),
            SlashCommandOptionChoice.create("Floor 6", "Floor 6"),
            SlashCommandOptionChoice.create("Floor 7", "Floor 7"))),
    EMAN(Arrays.asList(SlashCommandOptionChoice.create("Tier 3", "Tier 3"),
            SlashCommandOptionChoice.create("Tier 4", "Tier 4"))),
    BLAZE(Arrays.asList(SlashCommandOptionChoice.create("Tier 2", "Tier 2"),
            SlashCommandOptionChoice.create("Tier 3", "Tier 3"),
            SlashCommandOptionChoice.create("Tier 4", "Tier 4")));

    @Getter
    private final List<SlashCommandOptionChoice> choiceList;

    CarryType(List<SlashCommandOptionChoice> choiceList)
    {
        this.choiceList = choiceList;
    }
}