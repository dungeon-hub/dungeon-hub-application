package me.taubsie.dungeonhub.application.classes;

/**
 * This represents the type of server property.
 * The default is {@link #STRING}, as the properties are saved as a string.
 * This has effects on the way that values are displayed and on how they are set.
 * <p>
 * Still WIP
 */
public enum ServerPropertyType {
    //TODO finish implementing boolean
    STRING,
    NUMBER,
    BOOLEAN,
    CHANNEL,
    CATEGORY,
    ROLE;

    public String applyPropertyType(String value) {
        if(this == ServerPropertyType.CATEGORY) {
            return "<#" + value + ">";
        }

        if(this == ServerPropertyType.CHANNEL) {
            return "<#" + value + ">";
        }

        if(this == ServerPropertyType.ROLE) {
            return "<@&" + value + ">";
        }

        return value;
    }

    public boolean canAccept(String value) {
        //TODO implement
        return true;
    }
}