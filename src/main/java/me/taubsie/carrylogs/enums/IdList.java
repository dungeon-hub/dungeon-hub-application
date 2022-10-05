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
    TEST_SERVER(1023684107877761196L),
    TEST_F4_CATEGORY(1026291896336793631L),
    TEST_F5_CATEGORY(1026291912157696000L),
    TEST_F6_CATEGORY(1026291924216336395L),
    TEST_F7_CATEGORY(1026291937440960615L),
    TEST_MASTER_CATEGORY(1026291957594587167L),
    TEST_EMAN_CATEGORY(1026291971872018432L),
    TEST_BLAZE_CATEGORY(1026291986090704968L);

    @Getter
    private final long id;

    IdList(long id)
    {
        this.id = id;
    }
}