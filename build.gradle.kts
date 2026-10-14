plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "tk.slicecollections.maxteer"
version = "4.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    // Mojang hosts Brigadier, a transitive dependency of the BungeeCord API.
    maven {
        url = uri("https://libraries.minecraft.net/")
        metadataSources {
            mavenPom()
            artifact()
            ignoreGradleMetadataRedirection()
        }
    }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.21-R0.1")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("com.mysql:mysql-connector-j:8.0.33")
    implementation("com.github.HenryFabio:inventory-api:2.0.3")
}

sourceSets {
    main {
        java { setSrcDirs(listOf("src/main/java")) }
        resources { setSrcDirs(listOf("src/main/resources")) }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "bungee.yml")) {
        expand(mapOf(
            "artifactId" to project.name,
            "version" to project.version.toString(),
            "mainBukkit" to "tk.slicecollections.maxteer.Core",
            "mainBungee" to "tk.slicecollections.maxteer.bungee.Bungee"
        ))
    }
}

tasks.shadowJar {
    archiveFileName.set("mCore.jar")
    configurations = listOf(project.configurations.runtimeClasspath.get())
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")

    relocate("com.henryfabio.minecraft.inventoryapi", "tk.slicecollections.maxteer.shaded.inventoryapi")
    relocate("org.jetbrains.annotations", "tk.slicecollections.maxteer.shaded.annotations")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
