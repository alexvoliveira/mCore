# mCore — estado da refatoração

Esta branch está em refatoração incremental e **não deve ser implantada em produção até a validação completa do build e dos dados**.

## Implementado no código de produção

- Um único backend de persistência: MySQL por HikariCP.
- Remoção das classes de produção `MongoDBDatabase`, `MySQLDatabase` sem pool e do conversor online MySQL→MongoDB.
- Títulos personalizados lidos da tabela SQL `mCoreTitles`; tabelas de perfil continuam em `mCoreProfile`, `mCoreSkyWars`, `mCoreTheBridge`, `mCoreMurder` e `mCoreNetworkBooster`.
- Configuração JDBC com `sslMode=VERIFY_IDENTITY` e nome de driver do Connector/J 8.
- Consultas de alterações legadas de schema encaminhadas pelo contrato comum `Database`.
- Escrita assíncrona serializada por um executor único para manter a ordem de submissão de operações; a análise de perda de atualizações e backpressure ainda precisa ser concluída.
- Comando de conversão online removido; há agora um importador offline baseado em exports JSONL, sem MongoDB driver no plugin.
- Desativação de instalação automática de JARs sem validação criptográfica e de encerramento do processo com `System.exit(0)`.
- Scaffold Gradle Kotlin DSL e documentação inicial.

## Evidências e validação

- Commits publicados e diff remoto conferidos na branch de revisão.
- A compilação Gradle neste ambiente de edição **não foi executada**. Foi adicionado um workflow do GitHub Actions que gera os artefatos Spigot 1.8.8 e executa o Wrapper/testes; o resultado precisa ser consultado antes de considerar a validação concluída.
- Os testes de integração com Spigot/Bukkit 1.8.8, NMS `v1_8_R3`, BungeeCord, MySQL e migração de dados não foram executados; testes unitários da ferramenta offline foram adicionados ao workflow.
- O `pom.xml` permanece temporariamente para referência/rollback até que o build Gradle possa ser validado. Ele ainda precisa ser eliminado conforme o plano original.

## Pendências que bloqueiam a conclusão

1. Executar `./gradlew clean build` em ambiente com JDK/Gradle compatíveis e artefatos corretos de Spigot 1.8.8/NMS e BungeeCord 1.8.
2. Validar o importador com backups representativos, comparando contagens, perfis, títulos, boosters e amostras de estatísticas.
3. Revisar atomicidade, erro em escritas assíncronas, fechamento do executor e concorrência de `Profile`/fila.
4. Testar ciclo de vida de pré-login, entrada/saída, salvamento, transferência de servidores e desligamento.
5. Confirmar o resultado do workflow de build/testes; um job em execução não conta como validação aprovada.
6. Remover o `pom.xml` e os resíduos de build antigos somente após a validação do Gradle.
6. Fazer revisão final de referências remanescentes, SQL dinâmico e compatibilidade de APIs antigas.

A branch ainda não foi mesclada a `main`.
