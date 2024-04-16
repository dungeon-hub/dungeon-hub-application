package me.taubsie.dungeonhub.application.exceptions;

public class HypixelLinkedToOtherException extends CommandExecutionException {

    public HypixelLinkedToOtherException(String ign) {
        super("`" + ign + "` has their ingame discord-account set to something else.\n" +
                "If you need more information about linking your discord account, please use `/help verification`");
    }
}