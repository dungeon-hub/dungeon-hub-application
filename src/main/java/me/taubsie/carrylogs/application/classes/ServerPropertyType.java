package me.taubsie.carrylogs.application.classes;

public enum ServerPropertyType {
    STRING,
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