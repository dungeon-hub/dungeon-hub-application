package me.taubsie.dungeonhub.application.exceptions;

public class PlayerNotFoundException extends CommandExecutionException {
    private final String ign;

    public PlayerNotFoundException(String ign) {
        this.ign = ign;
    }


    @Override
    public String getMessage() {
        return "Player with name " + ign + " not found!";
    }
}