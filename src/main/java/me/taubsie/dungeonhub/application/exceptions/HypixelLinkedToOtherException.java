package me.taubsie.dungeonhub.application.exceptions;

public class HypixelLinkedToOtherException extends CommandExecutionException {

    public HypixelLinkedToOtherException(String ign) {
        super("`" + ign + "` is already linked to another discord-account.\n" +
                "If you need more information about linking your discord account, please use `/help verification`");
    }
}