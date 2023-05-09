package me.taubsie.carrylogs.application.classes;

public enum ServerPropertyType {
    //TODO finish implementing boolean
    STRING,
    BOOLEAN,
    CHANNEL,
    ROLE;

    public String applyPropertyType(String value) {
        if(this == ServerPropertyType.CHANNEL) {
            return "<#" + value + ">";
        }

        if(this == ServerPropertyType.ROLE) {
            return "<@&" + value + ">";
        }

        return value;
    }
}