package me.taubsie.dungeonhub.application.exceptions;

public class HypixelLinkedToOtherException extends CommandExecutionException {
    private final String ign;

    public HypixelLinkedToOtherException(String ign) {
        this.ign = ign;
    }

    @Override
    public String getMessage() {
        return "`" + ign + "` is already linked to another discord-account.\n" +
                "If you need more information about linking your discord account, please use `/help verification`";
    }
}