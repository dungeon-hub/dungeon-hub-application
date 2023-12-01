package me.taubsie.dungeonhub.application.enums;

import lombok.Getter;
import me.taubsie.dungeonhub.application.classes.FlagDetail;
import me.taubsie.dungeonhub.application.classes.FlagResponse;
import me.taubsie.dungeonhub.application.connection.FlaggingConnection;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public enum FlaggingApi {
    JERRY(
            "Jerry",
            uuid -> FlaggingConnection.getInstance().isJerryFlagged(uuid),
            aLong -> FlaggingConnection.getInstance().isJerryFlagged(aLong)
    );

    @Getter
    final String name;
    @Nullable
    final Function<UUID, Optional<FlagDetail>> uuidFunction;
    @Nullable
    final Function<Long, Optional<FlagDetail>> discordIdFunction;

    FlaggingApi(String name, @Nullable Function<UUID, Optional<FlagDetail>> uuidFunction, @Nullable Function<Long,
            Optional<FlagDetail>> discordIdFunction) {
        this.name = name;
        this.uuidFunction = uuidFunction;
        this.discordIdFunction = discordIdFunction;
    }

    public FlagResponse execute(@Nullable UUID uuid, @Nullable Long id) {
        CompletableFuture<Optional<FlagDetail>> uuidFlagged = null;
        if (uuidFunction != null && uuid != null) {
            uuidFlagged = CompletableFuture.supplyAsync(() -> uuidFunction.apply(uuid));
        }

        CompletableFuture<Optional<FlagDetail>> discordIdFlagged = null;
        if (discordIdFunction != null && id != null && id != 0) {
            discordIdFlagged = CompletableFuture.supplyAsync(() -> discordIdFunction.apply(id));
        }

        try {
            FlagDetail uuidFlaggedResponse = Optional.ofNullable(uuidFlagged)
                    .flatMap(CompletableFuture::join)
                    .orElse(null);

            FlagDetail discordIdFlaggedResponse = Optional.ofNullable(discordIdFlagged)
                    .flatMap(CompletableFuture::join)
                    .orElse(null);

            return new FlagResponse(
                    name,
                    uuidFlaggedResponse,
                    discordIdFlaggedResponse
            );
        }
        catch (CompletionException completionException) {
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't load scammer data.";
                }
            };
        }
    }
}