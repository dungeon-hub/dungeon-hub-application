import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"

    id("com.github.johnrengelman.shadow") version "8.1.1"
    //TODO fix errors
    //id("io.gitlab.arturbosch.detekt") version "1.23.6"

    id("dev.kordex.gradle.kordex") version "1.6.2"
}

group = "me.taubsie"
version = "1.0.0"
description = "dungeon-hub-application"

repositories {
    maven {
        url = uri("https://repo.hypixel.net/repository/Hypixel/")
        name = "Hypixel Repository"
    }

    mavenLocal()
}

kordEx {
    kordExVersion = "2.3.1-SNAPSHOT"
    jvmTarget = 17

    bot {
        // See https://docs.kordex.dev/data-collection.html
        dataCollection(DataCollection.Extra)

        mainClass = "me.taubsie.dungeonhub.application.connection.DiscordConnection"
    }

    i18n {
        classPackage = "net.dungeonhub.i18n"
        translationBundle = "dhub.strings"
    }
}

//TODO fix errors
/*detekt {
    buildUponDefaultConfig = true

    config.from(rootProject.files("detekt.yml"))
}*/

dependencies {
    //TODO fix errors
    //detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")

    //Lombok, might remove at some time
    implementation("org.projectlombok:lombok:1.18.28")

    //Internal API
    implementation(libs.dungeon.hub.api.client)

    //Hypixel API
    implementation(libs.hypixel.wrapper)

    //Functionality
    implementation("net.dungeon-hub:transcripts-kord:0.1.1")
    implementation("net.codebox:homoglyph:1.2.1")
    implementation("com.google.zxing:javase:3.5.2")
    implementation("com.google.guava:guava:33.0.0-jre")
    implementation("org.mnode.ical4j:ical4j:4.0.5")

    //HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    //Logging
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")

    //Annotations
    annotationProcessor("org.projectlombok:lombok:1.18.28")
    annotationProcessor("org.apache.logging.log4j:log4j-core:2.20.0")

    //Testing
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}