/*
 * KissenEssentials
 * Copyright (C) KissenEssentials team and contributors.
 *
 * This program is free software and is free to redistribute
 * and/or modify under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is intended for the purpose of joy,
 * WITHOUT WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.taubsie.carrylogs.enums;

import lombok.Getter;
import org.javacord.api.interaction.SlashCommandOptionChoice;

import java.util.Arrays;
import java.util.List;

public enum CarryType
{
    F4(Arrays.asList(SlashCommandOptionChoice.create("Completion", "comp"),
            SlashCommandOptionChoice.create("S", "s"),
            SlashCommandOptionChoice.create("S+", "s-plus"))),
    F5(Arrays.asList(SlashCommandOptionChoice.create("Completion", "comp"),
            SlashCommandOptionChoice.create("S", "s"),
            SlashCommandOptionChoice.create("S+", "s-plus"))),
    F6(Arrays.asList(SlashCommandOptionChoice.create("Completion", "comp"),
            SlashCommandOptionChoice.create("S", "s"),
            SlashCommandOptionChoice.create("S+", "s-plus"))),
    F7(Arrays.asList(SlashCommandOptionChoice.create("Completion", "comp"),
            SlashCommandOptionChoice.create("S", "s"),
            SlashCommandOptionChoice.create("S+", "s-plus"))),
    MASTER_MODE(Arrays.asList(SlashCommandOptionChoice.create("Floor 1", "f1"),
            SlashCommandOptionChoice.create("Floor 2", "f2"),
            SlashCommandOptionChoice.create("Floor 3", "f3"),
            SlashCommandOptionChoice.create("Floor 4", "f4"),
            SlashCommandOptionChoice.create("Floor 5", "f5"),
            SlashCommandOptionChoice.create("Floor 6", "f6"),
            SlashCommandOptionChoice.create("Floor 7", "f7"))),
    EMAN(Arrays.asList(SlashCommandOptionChoice.create("Tier 3", "t3"),
            SlashCommandOptionChoice.create("Tier 4", "t4"))),
    BLAZE(Arrays.asList(SlashCommandOptionChoice.create("Tier 2", "t2"),
            SlashCommandOptionChoice.create("Tier 3", "t3"),
            SlashCommandOptionChoice.create("Tier 4", "t4")));

    @Getter
    private final List<SlashCommandOptionChoice> choiceList;

    CarryType(List<SlashCommandOptionChoice> choiceList)
    {
        this.choiceList = choiceList;
    }
}