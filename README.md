# mCore

Biblioteca compartilhada de funções para plugins Minecraft.

## Refatoração de segurança e persistência

A branch `refactor/stage1-security-gradle` contém a primeira etapa de hardening de segurança e preparação do build Gradle. Ela consolida a persistência de produção em MySQL/HikariCP e inclui uma ferramenta offline para importar backups JSONL de MongoDB. Consulte [docs/EXECUTION-STATUS.md](docs/EXECUTION-STATUS.md) antes de implantar essa etapa.

## Migração para JDK 21 / Spigot 1.21.1

A branch `refactor/spigot-1.21.1-jdk21` é a linha de migração para Java 21 e Spigot 1.21.1. **Ainda não está pronta para produção.** O código NMS 1.8.8 de NPCs, skins, pathfinding e hologramas exige uma implementação compatível com 1.21.1; alterar somente o Gradle não basta.

- [Guia de build](docs/BUILD.md)
- [Estado da migração](docs/EXECUTION-STATUS.md)
- [Análise técnica da migração](docs/MIGRATION-1.21.1.md)
