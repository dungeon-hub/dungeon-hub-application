package me.taubsie.carrylogs.application.enums;

import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.service.ServerService;
import me.taubsie.dungeonhub.common.CarryRole;
import org.javacord.api.entity.permission.Role;

import java.util.ArrayList;
import java.util.List;

public enum RoleConversion {
    F4_ROLE(CarryRole.F4, ServerProperty.F4_ROLE),
    F5_ROLE(CarryRole.F5, ServerProperty.F5_ROLE),
    F6_ROLE(CarryRole.F6, ServerProperty.F6_ROLE),
    F7_ROLE(CarryRole.F7, ServerProperty.F7_ROLE),
    MASTER_ROLE(CarryRole.MASTER_MODE, ServerProperty.MASTER_ROLE),
    EMAN_T3_ROLE(CarryRole.EMAN_T3, ServerProperty.EMAN_T3_ROLE),
    EMAN_T4_ROLE(CarryRole.EMAN_T4, ServerProperty.EMAN_T4_ROLE),
    BLAZE_T2_ROLE(CarryRole.BLAZE_T2, ServerProperty.BLAZE_T2_ROLE),
    BLAZE_T3_ROLE(CarryRole.BLAZE_T3, ServerProperty.BLAZE_T3_ROLE),
    BLAZE_T4_ROLE(CarryRole.BLAZE_T4, ServerProperty.BLAZE_T4_ROLE),
    KUUDRA_BASIC_ROLE(CarryRole.BASIC, ServerProperty.KUUDRA_BASIC_ROLE),
    KUUDRA_HOT_ROLE(CarryRole.HOT, ServerProperty.KUUDRA_HOT_ROLE),
    KUUDRA_BURNING_ROLE(CarryRole.BURNING, ServerProperty.KUUDRA_BURNING_ROLE),
    KUUDRA_FIERY_ROLE(CarryRole.FIERY, ServerProperty.KUUDRA_FIERY_ROLE),
    KUUDRA_INFERNAL_ROLE(CarryRole.INFERNAL, ServerProperty.KUUDRA_INFERNAL_ROLE);

    private final CarryRole carryRole;
    private final ServerProperty serverProperty;

    RoleConversion(CarryRole carryRole, ServerProperty serverProperty) {
        this.carryRole = carryRole;
        this.serverProperty = serverProperty;
    }

    public CarryRole getCarryRole() {
        return carryRole;
    }

    public ServerProperty getServerProperty() {
        return serverProperty;
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
                F7_ROLE,
                MASTER_ROLE
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
            if(role.getCarryRole() != null
                    && roles.stream().anyMatch(userRole -> String.valueOf(userRole.getId()).equalsIgnoreCase(ServerService.getInstance().getServerProperty(serverId, role.getServerProperty())))) {
                userRoles.add(role);
            }
        }

        return userRoles;
    }
}