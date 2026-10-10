# mCore — estado da refatoração (etapa inicial)

Esta branch contém uma primeira etapa de hardening e preparação de build. **Não é a refatoração completa e não deve ser implantada diretamente em produção.**

## Incluído nesta etapa

- Desativa a instalação automática de JAR baixado enquanto não houver verificação criptográfica da origem e do artefato.
- Remove chamadas que encerravam todo o processo Java em falhas do plugin ou da conexão MySQL.
- Ativa configuração TLS com validação de identidade/certificado nos caminhos JDBC MySQL/MariaDB; a implantação depende de certificados e truststore válidos.
- Faz a preferência respeitar o valor padrão informado quando o perfil/chave estiver ausente ou inválido.
- Valida identificadores de tabela e coluna de leaderboard contra os schemas registrados.
- Fecha o recurso de banco no desligamento do processo Bungee.
- Adiciona configuração inicial Gradle Kotlin DSL/Shadow e mantém `pom.xml` para rollback.

## Validação realizada

- Confirmadas as âncoras exatas nas fontes remotas antes de editar.
- Correções aplicadas numa branch separada de `main`.
- **Build Gradle não executado.** Este ambiente não possui Gradle/Wrapper pronto nem os artefatos de implementação CraftBukkit/NMS e BungeeCord compatíveis instalados em `mavenLocal()`.
- Testes de integração em Spigot/Bukkit 1.8.8, BungeeCord, MySQL e MariaDB permanecem pendentes.

## Pendências que bloqueiam a refatoração integral

1. Migrar dados e funcionalidades dependentes de MongoDB, incluindo títulos personalizados e conversão de perfis, antes de remover MongoDB.
2. Implementar um runner versionado de migrações de banco com histórico, transações quando suportadas, validação prévia e rollback documentado.
3. Auditar concorrência e ciclo de vida de perfis, filas e gravações assíncronas.
4. Validar a compilação, testes e execução com as versões legadas exatas.
5. Só então considerar remover o Maven e promover a branch para `main`.

**Não mesclar ou implantar esta etapa até concluir as pendências e validar o build num ambiente compatível.**
