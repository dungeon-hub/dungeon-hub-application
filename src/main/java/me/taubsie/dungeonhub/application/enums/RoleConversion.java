package me.taubsie.dungeonhub.application.enums;

import lombok.Getter;
import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.common.OldCarryRole;
import org.javacord.api.entity.permission.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum RoleConversion {
    F4_ROLE(OldCarryRole.F4, ServerProperty.F4_ROLE),
    F5_ROLE(OldCarryRole.F5, ServerProperty.F5_ROLE),
    F6_ROLE(OldCarryRole.F6, ServerProperty.F6_ROLE),
    F7_ROLE(OldCarryRole.F7, ServerProperty.F7_ROLE),
    EMAN_T3_ROLE(OldCarryRole.EMAN_T3, ServerProperty.EMAN_T3_ROLE),
    EMAN_T4_ROLE(OldCarryRole.EMAN_T4, ServerProperty.EMAN_T4_ROLE),
    BLAZE_T2_ROLE(OldCarryRole.BLAZE_T2, ServerProperty.BLAZE_T2_ROLE),
    BLAZE_T3_ROLE(OldCarryRole.BLAZE_T3, ServerProperty.BLAZE_T3_ROLE),
    BLAZE_T4_ROLE(OldCarryRole.BLAZE_T4, ServerProperty.BLAZE_T4_ROLE),
    KUUDRA_BASIC_ROLE(OldCarryRole.BASIC, ServerProperty.KUUDRA_BASIC_ROLE),
    KUUDRA_HOT_ROLE(OldCarryRole.HOT, ServerProperty.KUUDRA_HOT_ROLE),
    KUUDRA_BURNING_ROLE(OldCarryRole.BURNING, ServerProperty.KUUDRA_BURNING_ROLE),
    KUUDRA_FIERY_ROLE(OldCarryRole.FIERY, ServerProperty.KUUDRA_FIERY_ROLE),
    KUUDRA_INFERNAL_ROLE(OldCarryRole.INFERNAL, ServerProperty.KUUDRA_INFERNAL_ROLE);

    private final OldCarryRole oldCarryRole;
    @Getter
    private final ServerProperty serverProperty;

    RoleConversion(OldCarryRole oldCarryRole, ServerProperty serverProperty) {
        this.oldCarryRole = oldCarryRole;
        this.serverProperty = serverProperty;
    }

    public OldCarryRole getCarryRole() {
        return oldCarryRole;
    }

    public static RoleConversion[] getSlayerCarryRoles() {
        return new RoleConversion[]{
                EMAN_T3_ROLE,
                EMAN_T4_ROLE,
                BLAZE_T2_ROLE,
                BLAZE_T3_ROLE,
                BLAZE_T4_ROLE
        };
    }

    public static RoleConversion[] getDungeonCarryRoles() {
        return new RoleConversion[]{
                F4_ROLE,
                F5_ROLE,
                F6_ROLE,
                F7_ROLE
        };
    }

    public static RoleConversion[] getKuudraCarryRoles() {
        return new RoleConversion[]{
                KUUDRA_BASIC_ROLE,
                KUUDRA_HOT_ROLE,
                KUUDRA_BURNING_ROLE,
                KUUDRA_FIERY_ROLE,
                KUUDRA_INFERNAL_ROLE
        };
    }

    public static RoleConversion[] getCarryRoles() {
        RoleConversion[] result =
                new RoleConversion[getDungeonCarryRoles().length + getSlayerCarryRoles().length + getKuudraCarryRoles().length];

        System.arraycopy(getDungeonCarryRoles(), 0, result, 0, getDungeonCarryRoles().length);
        System.arraycopy(getSlayerCarryRoles(), 0, result, getDungeonCarryRoles().length, getSlayerCarryRoles().length);
        System.arraycopy(getKuudraCarryRoles(), 0, result,
                getDungeonCarryRoles().length + getSlayerCarryRoles().length, getKuudraCarryRoles().length);

        return result;
    }

    public static List<RoleConversion> getCarryRoles(List<Role> roles, long serverId) {
        List<RoleConversion> userRoles = new ArrayList<>();

        for(RoleConversion role : getCarryRoles()) {
            if(role.getCarryRole() != null) {
                Optional<String> serverProperty = role.getServerProperty().getValue(serverId);

                if(serverProperty.isPresent()
                        && roles.stream().anyMatch(userRole -> String.valueOf(userRole.getId()).equalsIgnoreCase(serverProperty.get()))) {
                    userRoles.add(role);
                }
            }
        }

        return userRoles;
    }
}