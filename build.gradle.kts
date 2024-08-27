plugins {
    application
    kotlin("jvm")
}

group = "me.taubsie"
version = "1.0.0"
description = "dungeon-hub-application"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.hypixel.net/repository/Hypixel/")
        name = "Hypixel Repository"
    }
    mavenLocal()
}

dependencies {
    //Lombok, might remove at some time
    implementation("org.projectlombok:lombok:1.18.28")

    //Internal API
    implementation("me.taubsie:dungeon-hub-common:1.0.0")

    //Functionality
    implementation("net.dungeon-hub:transcripts-kord:0.1")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("net.codebox:homoglyph:1.2.1")
    implementation("net.hypixel:hypixel-api-core:4.4")
    implementation("me.nullicorn:Nedit:2.2.0")
    implementation("com.google.zxing:javase:3.5.2")

    //HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    //Discord Framework
    implementation("dev.kord:kord-core:0.13.1")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.6.0")

    //Logging
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")

    //Kotlin
    implementation(kotlin("stdlib-jdk8"))

    //Annotations
    annotationProcessor("org.projectlombok:lombok:1.18.28")
    annotationProcessor("org.apache.logging.log4j:log4j-core:2.20.0")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("me.taubsie.dungeonhub.application.connection.DiscordConnection")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

//TODO remove this once all classes are written in Kotlin
sourceSets {
    main {
        java {
            srcDir("src")
        }
    }
}