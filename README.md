# Dungeon Hub Application

[![Gradle Tests](https://github.com/dungeon-hub/dungeon-hub-application/actions/workflows/gradle-test.yml/badge.svg)](https://github.com/dungeon-hub/dungeon-hub-application/actions/workflows/gradle-test.yml)

The Dungeon Hub Application is the Kotlin-based Discord bot behind the [Dungeon Hub](https://discord.dungeon-hub.net/) community. It connects Discord with Minecraft and Hypixel SkyBlock data to provide account verification, community management, carry services, and moderation workflows.

Although built for Dungeon Hub, the bot supports multiple Discord servers. Its behavior is configurable per server, while shared data is provided by the wider Dungeon Hub platform.

You can either **[invite our free-to-use instance](https://invite.dungeon-hub.net/)** or **self-host** the bot yourself (see [Running the Application](#running-the-application)).

## Features

- **Minecraft account verification**: links Discord members to Minecraft accounts through Mojang and Hypixel data, then keeps nicknames and roles synchronized.
- **Hypixel SkyBlock tools**: provides player lookups, profile statistics, status information, and configurable role requirements based on player data.
- **Carry management**: supports configurable carry types, tiers, difficulties, pricing, activity logging, scores, and leaderboards.
- **Reputation and community requests**: manages reputation records and community crafting and transfer requests.
- **Ticket workflows**: creates and manages support tickets with claiming, participant management, lifecycle controls, and transcripts.
- **Moderation and administration**: includes warnings, member purges, audit logging, server statistics, role management, and configuration commands.
- **Managed server content**: publishes embeds, static messages, and CDN-hosted resources, and automates recurring server presentation tasks.
- **Internationalization**: provides localized bot responses through Kord Extensions' i18n support and maintained translation bundles.

## How It Fits Together

This repository contains the Discord-facing application. It uses:

- the [Dungeon Hub API client](https://github.com/dungeon-hub/dungeon-hub-api) to communicate with the [Dungeon Hub backend](https://github.com/dungeon-hub/dungeon-hub-server);
- the [Dungeon Hub Hypixel wrapper](https://github.com/dungeon-hub/hypixel-wrapper) for Mojang and Hypixel integrations;
- [Kord Extensions](https://github.com/Kord-Extensions/kord-extensions) for Discord commands, events, components, and localization.

Commands such as `/player`, `/stats` and `/lookup` are a good starting point; the full list lives in the [commands directory](src/main/kotlin/net/dungeonhub/application/commands).

Commands and listeners are discovered at startup, while scheduled services handle tasks such as configuration reloads, role synchronization, statistics, birthdays, and static-message updates.

## Technology

- Kotlin
- JDK 25
- Gradle with the Kotlin DSL
- Kord Extensions
- Kotlin coroutines
- Ktor and OkHttp
- JUnit Platform

## Running the Application

You need JDK 25. The Gradle wrapper is included, so a separate Gradle installation is not required.

```bash
./gradlew run
```

On its first start, the application creates its configuration file at:

```text
${user.home}/dungeon-hub/config/application_config.properties
```

Set `discord-bot.token` to a valid Discord bot token and configure the service URLs and credentials needed for your deployment. The application reloads the file every few minutes and adds newly introduced settings automatically.

### Configuration

| Property | Purpose |
| --- | --- |
| `discord-bot.token` | Discord bot token (**required**) |
| `api.url` | URL of the Dungeon Hub backend |
| `auth.login_url`, `auth.client_id`, `auth.client_secret` | Credentials for authenticating against the backend |
| `cdn.public-url`, `cdn.static-url` | Public CDN and static-content URLs used for embeds and resources |
| `hypixel-api.key` | Hypixel API key |
| `hypixel-wrapper.cache-type` | Cache type used by the Hypixel wrapper |
| `mongodb.connection-string`, `mongodb.database-name` | MongoDB settings for the Hypixel wrapper cache |
| `safety-api.url`, `safety-api.key` | Optional: safety/flagging integration |
| `jerry.url`, `jerry.key` | Optional: scammer-list integration |
| `blockgame.url`, `blockgame.key` | Optional: Block Game integration |

The bot requests Discord's Guild Members and Message Content privileged intents. Enable both for the application in the Discord Developer Portal before running it.

Some features depend on Dungeon Hub backend services and their credentials; a standalone bot token is not sufficient for the complete production feature set.

## Building and Testing

```bash
./gradlew build
```

The build compiles the application and runs its test suite. The configured entry point is `net.dungeonhub.application.connection.DiscordConnection`.

## Development

The bot needs a running Dungeon Hub backend. For local development, check out [dungeon-hub-server](https://github.com/dungeon-hub/dungeon-hub-server) as a sibling directory (`../dungeon-hub-server`) and point `api.url` at it.

The repository ships IntelliJ IDEA run configurations in [`.run`](.run) that assume this layout:

| Run configuration | What it does |
| --- | --- |
| `start-test-database` | Starts the database via the server's `docker-compose.dev.yml` |
| `start-backend-dev` | Starts the backend with the `dev` Spring profile |
| `run-backend-test` | Runs the backend with the `test` Spring profile (`spring-boot:test-run`) |
| `build-server` | Builds the backend with Maven |
| `start-bot` | Starts the bot |
| `build-bot` | Builds and tests the bot |

Without IntelliJ, the equivalent is to start the database with Docker Compose, run the backend with `SPRING_PROFILES_ACTIVE=test mvn spring-boot:test-run` from the server directory, and then run `./gradlew run` here.

## Contributing

Contributions are welcome!

- For bigger changes or new features, please open an issue first so we can agree on the approach.
- Create a branch, make your change, and open a pull request against `main`.
- Make sure `./gradlew build test` passes; the same check runs in CI on every pull request.
- Add or update tests when you change behavior.
- Don't edit translated bundles by hand. Translations are managed on [Weblate](https://weblate.dungeon-hub.net/); only the English source strings belong in pull requests.
- Please never commit tokens, API keys, or other credentials.

## Project Resources

- [Documentation](https://docs.dungeon-hub.net/)
- [Dungeon Hub Discord](https://discord.dungeon-hub.net/)
- [Translations on Weblate](https://weblate.dungeon-hub.net/)
- [Dungeon Hub backend](https://github.com/dungeon-hub/dungeon-hub-server)
- [Dungeon Hub API client](https://github.com/dungeon-hub/dungeon-hub-api)
- [Dungeon Hub Hypixel wrapper](https://github.com/dungeon-hub/hypixel-wrapper)
