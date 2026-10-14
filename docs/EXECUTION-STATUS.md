# mCore — migration status: JDK 21 / Spigot 1.21.1

## Branch

- Migration branch: `refactor/spigot-1.21.1-jdk21`.
- Base: `refactor/stage1-security-gradle`.
- The stage1 persistence/security changes are retained as the starting point.

## Changes made so far

- Gradle Java toolchain and compiler release moved to Java 21.
- Compile-only target changed to Spigot API 1.21.1.
- BungeeCord API updated to the 1.21 line.
- ProtocolLib dependency updated to 5.4.0 using its current Maven coordinates.
- PlaceholderAPI dependency updated to 2.12.3; removed the brittle runtime assertion that required exactly 2.10.5.
- `plugin.yml` declares `api-version: '1.21'`.
- GitHub Actions now runs tests and Gradle using JDK 21.
- Maven Central, Spigot snapshots, PlaceholderAPI, JitPack and Mojang's library repository are configured. The Mojang repository supplies Brigadier used transitively by the BungeeCord API.
- `MConfig` now uses the JDK's UTF-8 `InputStreamReader`, not a relocated CraftBukkit-internal class.
- `MinecraftVersion` now parses version strings returned by modern Bukkit, where the CraftBukkit package no longer necessarily includes a version token.
- The five tests for the offline JSONL import tool passed in the first JDK 21 workflow run.

## Blocking migration work

**Do not deploy this branch yet.** The old runtime implementation remains coupled to 1.8.8:

- `NMS.setupNMS()` only selects `NMS1_8R3` and returns false on 1.21.1. `Core.enable()` therefore disables the plugin on the target server until a compatible adapter is implemented.
- Twenty source files under `nms/v1_8_R3` use internal `net.minecraft.server.v1_8_R3` and `org.bukkit.craftbukkit.v1_8_R3` classes. The requested `Spigot-1.21.1` repository contains the public Spigot API, but not CraftBukkit/NMS classes, so it cannot be used as a drop-in substitute for this implementation.
- The NMS adapter supports custom fake-player NPCs, skin properties, pathfinding, player-list packets, tracker changes, hologram armor stands, touch slimes and item pickups. Those behaviors need a real 1.21.1 implementation and runtime tests; removing the old package or returning success from `setupNMS()` would leave broken behavior.
- ProtocolLib packet adapters (fake players, holograms and NPCs) still need to be compiled against 5.4.0 and tested against 1.21.1 packet layouts.
- Remaining Bukkit API compile errors and runtime behavior are not yet validated; CI failure details are tracked in the current workflow runs.

## Fork compatibility assessment

- `alexvoliveira/placeholderapi` `main` currently declares a development version (`2.12.4-DEV-...`) and compiles optional Paper/Folia sources against a newer API line. The normal mCore dependency uses the stable 2.12.3 artifact to avoid depending on a moving development build.
- `alexvoliveira/protocollib` `main` currently declares `5.5.0-SNAPSHOT`, targets Minecraft `26.2`, and uses a Java 25 toolchain. That branch does not match a JDK 21 / Minecraft 1.21.1 dependency target, so it is not used as a binary dependency.
- These forks can still be useful as source references, but their current branch versions are not evidence of a compatible released artifact for 1.21.1.

## Required next validation gates

1. Finish the Bukkit-based NMS replacement or port the necessary server-internal adapter to 1.21.1.
2. Preserve and test fake-player NPC, skin, movement/navigation, hologram, touch and pickup contracts.
3. Resolve all Java compile errors reported by the new workflow.
4. Test PlaceholderAPI expansion registration and all ProtocolLib adapters on a real Spigot 1.21.1 server.
5. Verify Bukkit lifecycle, BungeeCord integration, database startup/shutdown, profile persistence, inventory menus, and clean plugin disable.
6. Only after these gates pass, remove the legacy Maven descriptor and call this migration complete.
