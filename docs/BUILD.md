# Build — JDK 21 / Spigot 1.21.1

## Status

A branch `refactor/spigot-1.21.1-jdk21` is the migration branch, based on `refactor/stage1-security-gradle`. It is **not production-ready yet**: changing the dependency versions does not port the old NMS implementation. See [MIGRATION-1.21.1.md](MIGRATION-1.21.1.md) and [EXECUTION-STATUS.md](EXECUTION-STATUS.md).

## Requirements

- JDK 21.
- Network access to Maven Central, the Spigot snapshots repository, Mojang's library repository, PlaceholderAPI's repository, and JitPack.
- Spigot 1.21.1 server for runtime tests.
- ProtocolLib 5.4.0 and PlaceholderAPI 2.12.3 installed on the target server.

The Gradle Wrapper is pinned to Gradle 8.14.3. The build uses Java 21 toolchains and `options.release = 21`.

## Compile and package

From the repository root:

```sh
./gradlew --no-daemon clean build
```

The packaged plugin artifact is `build/libs/mCore.jar`.

## API and dependency choices

- Spigot: `org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT`.
- BungeeCord API: `net.md-5:bungeecord-api:1.21-R0.1`.
- ProtocolLib: `net.dmulloy2:ProtocolLib:5.4.0`.
- PlaceholderAPI: `me.clip:placeholderapi:2.12.3`.

The Mojang Maven repository is configured because the BungeeCord API has a transitive Brigadier dependency. ProtocolLib 5.4.0 uses the `net.dmulloy2` Maven coordinates while retaining the `com.comphenix.protocol` Java packages.

The existing `pom.xml` is retained only as a legacy reference while Gradle and the source migration are being validated. Its Java 8, Spigot 1.8.8, ProtocolLib 4.5.0, PlaceholderAPI 2.10.5 and machine-specific system path do not represent this branch's target. Do not build this branch with Maven.

## Secrets and database

Do not commit database credentials. Use environment/runtime configuration and a restricted SQL account. TLS certificate and hostname validation for the Aiven MySQL connection must remain enabled.

For the existing Aiven setup:
1. Download the service CA certificate from the Aiven console.
2. Save it as `plugins/mCore/ca.pem`, or set `MCORE_MYSQL_CA_CERT` to its absolute path.
3. Set `MCORE_MYSQL_PASSWORD` in the environment of the actual Java server process.
4. Restart the process after changing environment variables.

Do not disable TLS verification to work around a certificate configuration issue.
