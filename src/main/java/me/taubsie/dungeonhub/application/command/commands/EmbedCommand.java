package me.taubsie.dungeonhub.application.command.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.DungeonHubService;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

@CommandParameters(name = "embed", description = "Makes it possible to manage embeds.", enabledForPermissions =
        {PermissionType.MANAGE_MESSAGES})
public class EmbedCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption firstOption = getOptionAtIndex(0);

        switch (firstOption.getName().toLowerCase()) {
            case "get" -> get(firstOption);
            case "send" -> send(firstOption);
            case "edit" -> edit(firstOption);
            case "add" -> add(firstOption);
            default -> throw new InvalidSubCommandException();
        }
    }

    private void setFields(EmbedBuilder embed, JsonElement value) {
        value.getAsJsonArray().asList().stream()
                .map(JsonElement::getAsJsonObject)
                .forEach(jsonObject -> embed.addField(
                        jsonObject.getAsJsonPrimitive("name").getAsString(),
                        jsonObject.getAsJsonPrimitive("value").getAsString(),
                        jsonObject.getAsJsonPrimitive("inline").getAsBoolean()
                ));
    }

    private void setFooter(EmbedBuilder embed, JsonElement value) {
        JsonObject footer = value.getAsJsonObject();

        String text = footer.getAsJsonPrimitive("text").getAsString();

        if (footer.has("iconUrl")) {
            embed.setFooter(text, footer.getAsJsonPrimitive("iconUrl").getAsString());
        } else {
            embed.setFooter(text);
        }
    }

    private void setThumbnail(EmbedBuilder embed, JsonElement value) {
        JsonObject footer = value.getAsJsonObject();

        embed.setThumbnail(footer.getAsJsonPrimitive("url").getAsString());
    }

    private void applyJson(EmbedBuilder embed, String key, JsonElement value) {
        switch (key) {
            case "title" -> embed.setTitle(value.getAsString());
            case "description" -> embed.setDescription(value.getAsString());
            case "author" -> embed.setAuthor(value.getAsString());
            case "url" -> embed.setUrl(value.getAsString());
            case "color" ->
                    embed.setColor(DungeonHubService.getInstance().getGson().fromJson(value.getAsString(), Color.class));
            case "fields" -> setFields(embed, value);
            case "footer" -> setFooter(embed, value);
            case "timestamp" ->
                    embed.setTimestamp(DungeonHubService.getInstance().getGson().fromJson(value.getAsString(), Instant.class));
            case "thumbnail" -> setThumbnail(embed, value);
        }
    }

    private void add(SlashCommandInteractionOption firstOption) {
        String source = getStringOption(firstOption, "embed");

        List<EmbedBuilder> embeds = new ArrayList<>();

        try {
            JsonElement embedSource = DungeonHubService.getInstance().getGson().fromJson(source, JsonElement.class);

            if (embedSource.isJsonObject()) {
                EmbedBuilder embedBuilder = new EmbedBuilder();

                embedSource.getAsJsonObject()
                        .entrySet()
                        .forEach(entry -> applyJson(embedBuilder, entry.getKey(), entry.getValue()));

                embeds.add(embedBuilder);
            } else if (embedSource.isJsonArray()) {
                embedSource.getAsJsonArray()
                        .forEach(jsonElement -> {
                            if (jsonElement.isJsonObject()) {
                                EmbedBuilder embedBuilder = new EmbedBuilder();

                                jsonElement.getAsJsonObject()
                                        .entrySet()
                                        .forEach(entry -> applyJson(embedBuilder, entry.getKey(), entry.getValue()));

                                embeds.add(embedBuilder);
                            }
                        });
            }
        }
        catch (Exception exception) {
            throw new CommandExecutionException(exception);
        }

        if (embeds.isEmpty()) {
            throw new CommandExecutionException("Please provide any embeds to send.");
        }

        String link = getStringOption(firstOption, "link");

        Message message = DiscordConnection.getInstance().getBot().getMessageByLink(link)
                .orElseThrow(() -> new InvalidOptionException("link"))
                .join();

        if (!message.getAuthor().isYourself()) {
            throw new InvalidOptionException("link", "How should I edit a message that wasn't sent by myself?");
        }

        if (message.getEmbeds().isEmpty()) {
            throw new InvalidOptionException("link", "The given message doesn't have any embeds to edit.");
        }

        List<EmbedBuilder> newEmbeds = new ArrayList<>(message.getEmbeds().stream().map(Embed::toBuilder).toList());

        newEmbeds.addAll(embeds);

        message.edit(newEmbeds);

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Embed(s) added!"));
    }

    private void edit(SlashCommandInteractionOption firstOption) {
        EmbedBuilder embed = new EmbedBuilder();

        try {
            JsonObject embedSource = DungeonHubService.getInstance().getGson().fromJson(getStringOption(firstOption, "embed"), JsonObject.class);

            embedSource.entrySet().forEach(entry -> applyJson(embed, entry.getKey(), entry.getValue()));
        }
        catch (Exception exception) {
            throw new CommandExecutionException(exception);
        }

        int count;
        try {
            count = Math.toIntExact(getLongOption(firstOption, "count"));
        }
        catch (InvalidOptionException ignored) {
            count = 0;
        }

        String link = getStringOption(firstOption, "link");

        Message message = DiscordConnection.getInstance().getBot().getMessageByLink(link)
                .orElseThrow(() -> new InvalidOptionException("link"))
                .join();

        if (!message.getAuthor().isYourself()) {
            throw new InvalidOptionException("link", "How should I edit a message that wasn't sent by myself?");
        }

        if (message.getEmbeds().isEmpty()) {
            throw new InvalidOptionException("link", "The given message doesn't have any embeds to edit.");
        }

        List<EmbedBuilder> embeds = new ArrayList<>(message.getEmbeds().stream().map(Embed::toBuilder).toList());

        if (count >= embeds.size()) {
            throw new InvalidOptionException("link", "The given message doesn't have that many embeds.");
        }

        embeds.set(count, embed);

        message.edit(embeds);

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Embed edited!"));
    }

    private void send(SlashCommandInteractionOption firstOption) {
        ServerChannel channel;

        try {
            channel = getChannelOption(firstOption, "channel");
        }
        catch (InvalidOptionException ignored) {
            channel = getChannel().asServerChannel().orElseThrow();
        }

        String source = getStringOption(firstOption, "embed");

        List<EmbedBuilder> embeds = new ArrayList<>();

        try {
            JsonElement embedSource = DungeonHubService.getInstance().getGson().fromJson(source, JsonElement.class);

            if (embedSource.isJsonObject()) {
                EmbedBuilder embedBuilder = new EmbedBuilder();

                embedSource.getAsJsonObject()
                        .entrySet()
                        .forEach(entry -> applyJson(embedBuilder, entry.getKey(), entry.getValue()));

                embeds.add(embedBuilder);
            } else if (embedSource.isJsonArray()) {
                embedSource.getAsJsonArray()
                        .forEach(jsonElement -> {
                            if (jsonElement.isJsonObject()) {
                                EmbedBuilder embedBuilder = new EmbedBuilder();

                                jsonElement.getAsJsonObject()
                                        .entrySet()
                                        .forEach(entry -> applyJson(embedBuilder, entry.getKey(), entry.getValue()));

                                embeds.add(embedBuilder);
                            }
                        });
            }
        }
        catch (Exception exception) {
            throw new CommandExecutionException(exception);
        }

        if (embeds.isEmpty()) {
            throw new CommandExecutionException("Please provide any embeds to send.");
        }

        try {
            channel.asTextChannel().orElseThrow(() -> new CommandExecutionException("Unknown channel type"))
                    .sendMessage(embeds).join();
        }
        catch (CompletionException ignored) {
            throw new CommandExecutionException("Embed(s) couldn't be sent; please check if all required fields are set.");
        }

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Embed sent!"));
    }

    private void get(SlashCommandInteractionOption firstOption) {
        Server server = getServer();

        String link = getStringOption(firstOption, "link");

        int count;
        try {
            count = Math.toIntExact(getLongOption(firstOption, "count"));
        }
        catch (InvalidOptionException ignored) {
            count = -1;
        }

        Optional<String> type = getOptionalStringOption(firstOption, "type");

        boolean beautiful = type.map(s -> s.equalsIgnoreCase("beautiful")).orElse(false);

        Message message = server.getApi().getMessageByLink(link)
                .orElseThrow(() -> new InvalidOptionException("link"))
                .join();

        if (message.getEmbeds().isEmpty()) {
            throw new InvalidOptionException("link", "The given message doesn't have an embed.");
        }

        if (count != -1 && count >= message.getEmbeds().size()) {
            throw new InvalidOptionException("link", "The given message doesn't have that many embeds.");
        }

        List<Embed> embeds;
        if (count == -1) {
            embeds = message.getEmbeds();
        } else {
            embeds = List.of(message.getEmbeds().get(count));
        }

        EmbedBuilder embedBuilder = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor());

        if (beautiful && embeds.size() == 1) {
            DungeonHubService.getInstance().getGson().toJsonTree(embeds.get(0))
                    .getAsJsonObject()
                    .entrySet()
                    .forEach(entry -> embedBuilder.addField(entry.getKey(), entry.getValue().toString()));
        } else {
            String embedSource = DungeonHubService.getInstance().getGson().toJson(embeds.size() == 1 ? embeds.get(0) : embeds);

            String description = embedSource;

            if (type.map(s -> s.equalsIgnoreCase("cdn")).orElse(false)) {
                description = ContentConnection.getInstance().uploadFile(embedSource.getBytes(StandardCharsets.UTF_8)).map(s -> ContentConnection.getInstance().getCdnUrl(s).toString()).orElse(embedSource);
            }

            embedBuilder.setDescription(description);
        }

        respond(embedBuilder);
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption linkOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("link")
                .setDescription("Please paste the link to the message here.")
                .setRequired(true)
                .build();

        SlashCommandOption typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("Select how you want to get the embed data.")
                .setChoices(Stream.of("beautiful", "source", "cdn").map(s -> new SlashCommandOptionChoiceBuilder().setName(s).setValue(s).build()).toList())
                .setRequired(false)
                .build();

        SlashCommandOption countOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("count")
                .setDescription("Select which embed you want to get (0-based counting).")
                .setLongMinValue(0)
                .setLongMaxValue(100)
                .setRequired(false)
                .build();

        SlashCommandOption getOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Gets the embed data of a message.")
                .setOptions(List.of(linkOption, typeOption, countOption))
                .build();

        SlashCommandOption channelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setName("channel")
                .setDescription("The channel to send the embed into.")
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL, ChannelType.PRIVATE_CHANNEL, ChannelType.SERVER_VOICE_CHANNEL, ChannelType.SERVER_NEWS_CHANNEL, ChannelType.SERVER_PUBLIC_THREAD))
                .setRequired(false)
                .build();

        SlashCommandOption embedOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("embed")
                .setDescription("The embed data to send.")
                .setRequired(true)
                .build();

        SlashCommandOption sendOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("send")
                .setDescription("Sends a custom embed.")
                .setOptions(List.of(embedOption, channelOption))
                .build();

        SlashCommandOption addOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("add")
                .setDescription("Add an embed to a message sent by this bot.")
                .setOptions(List.of(linkOption, embedOption))
                .build();

        SlashCommandOption editOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("edit")
                .setDescription("Edit an embed sent by this bot.")
                .setOptions(List.of(linkOption, embedOption, countOption))
                .build();

        return List.of(getOption, sendOption, addOption, editOption);
    }
}