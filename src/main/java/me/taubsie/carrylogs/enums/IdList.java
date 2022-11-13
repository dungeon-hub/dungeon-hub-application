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

public enum IdList
{
    SERVER(693263712626278553L),
    TRANSCRIPTS_CHANNEL(796770232340578365L),
    APPROVING_CHANNEL(/* TODO */0L),
    F4_CATEGORY(CarryType.F4, 805834037108670464L),
    F5_CATEGORY(CarryType.F5, 805833843633815664L),
    F6_CATEGORY(CarryType.F6, 805833672678309908L),
    F7_CATEGORY(CarryType.F7, 805833601748828200L),
    MASTER_CATEGORY(CarryType.MASTER_MODE, 842840550704939053L),
    EMAN_CATEGORY(CarryType.EMAN, 992922867857641594L),
    BLAZE_CATEGORY(CarryType.BLAZE, 992922801075912764L),

    TEST_SERVER(1023684107877761196L),
    TEST_TRANSCRIPTS_CHANNEL(1036375112619937792L),
    TEST_APPROVING_CHANNEL(1036387847000825877L),
    TEST_F4_CATEGORY(CarryType.F4, 1026291896336793631L),
    TEST_F5_CATEGORY(CarryType.F5, 1026291912157696000L),
    TEST_F6_CATEGORY(CarryType.F6, 1026291924216336395L),
    TEST_F7_CATEGORY(CarryType.F7, 1026291937440960615L),
    TEST_MASTER_CATEGORY(CarryType.MASTER_MODE, 1026291957594587167L),
    TEST_EMAN_CATEGORY(CarryType.EMAN, 1026291971872018432L),
    TEST_BLAZE_CATEGORY(CarryType.BLAZE, 1026291986090704968L);

    @Getter
    private final long ID;

    @Getter
    private final CarryType CARRY_TYPE;

    IdList(long id)
    {
        this(null, id);
    }

    IdList(CarryType carryType, long id)
    {
        this.CARRY_TYPE = carryType;
        this.ID = id;
    }

    public static boolean isCarryCategory(long id) {
        for(IdList idList : values()) {
            if(idList.CARRY_TYPE != null && idList.ID == id) {
                return true;
            }
        }
        return false;
    }
}