package me.taubsie.carrylogs.application.exceptions;

public class InvalidOptionException extends IllegalArgumentException
{
    private final String name;
    private String additionalMessage;

    public InvalidOptionException(String name)
    {
        this(name, null);
    }

    public InvalidOptionException(String name, String additionalMessage)
    {
        this.name = name;
        this.additionalMessage = additionalMessage;
    }

    public void setAdditionalMessage(String additionalMessage)
    {
        this.additionalMessage = additionalMessage;
    }

    public String getMessage()
    {
        if (additionalMessage == null)
        {
            return String.format("The option \"%s\" you entered is invalid.", name);
        }
        else
        {
            return String.format("The option \"%s\" you entered is invalid:%n%s", name, additionalMessage);
        }
    }
}