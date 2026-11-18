# mCore

O mCore é a base compartilhada de funcionalidades da rede SliceCollections para servidores Minecraft legados. Ele reúne serviços que outros plugins da rede utilizam: perfis de jogadores, persistência de dados, permissões, filas, transferência entre servidores, menus, NPCs, hologramas, títulos, boosters, entregas, conquistas e integração com PlaceholderAPI.

O projeto possui pontos de entrada para **Spigot/Bukkit** e **BungeeCord**. No servidor de jogo, o mCore administra o estado do jogador e os recursos visuais; no proxy, oferece os comandos, listeners e estruturas de rede que precisam atuar no nível do BungeeCord. Os dois lados compartilham modelos e serviços, incluindo a camada de persistência MySQL.

> **Compatibilidade:** a implementação Bukkit utiliza NMS `v1_8_R3` e foi preparada para Spigot 1.8.8, Java 8, ProtocolLib 4.5.0 e PlaceholderAPI 2.10.5. Não presuma compatibilidade com versões modernas do Minecraft sem uma migração específica das APIs NMS e Bukkit.

## O que o mCore oferece

### Perfis e dados dos jogadores
Ao carregar um jogador, o mCore cria ou recupera um `Profile` com os dados persistidos em MySQL. O perfil reúne informações gerais, preferências, títulos, entregas, boosters, conquistas e estatísticas dos modos de jogo.

O módulo de persistência é baseado em MySQL com HikariCP. Os dados são organizados em tabelas por domínio, como `mCoreProfile`, `mCoreSkyWars`, `mCoreTheBridge` e `mCoreMurder`. Tabelas adicionais guardam boosters de rede e títulos personalizados.

### Cargos, permissões e identidade visual
Os cargos são carregados de configuração e associados a permissões. O mCore fornece nomes prefixados/coloridos, controle de visibilidade, cache de cargo e suporte a jogadores fictícios usados em recursos de rede.

### Fila e transferência entre servidores
O fluxo de transferência passa por filas de jogadores. Antes de solicitar a conexão ao proxy, o mCore salva o perfil e só avança quando a gravação termina. Quando o salvamento falha, a transferência é cancelada para evitar trocar o jogador de servidor deixando a persistência pendente.

As filas de membros e VIP têm ordenação separada. A permissão da fila VIP pode ser configurada em `core.yml`.

### Recursos de jogo e interface
O código também contém módulos para:

- conquistas e estatísticas de SkyWars, The Bridge e Murder;
- títulos, entregas e boosters individuais ou de rede;
- preferências e hotbars;
- parties e integração com plugins de amigos;
- menus de perfil, servidores, conquistas, títulos e boosters;
- NPCs, skins, traits e hologramas;
- scoreboards, placeholders e mensagens de ação;
- listagem e balanceamento de servidores.

### Integrações
No ambiente Bukkit, o mCore registra uma expansão própria para PlaceholderAPI e utiliza ProtocolLib para adaptadores de pacotes, NPCs e hologramas. A comunicação com o proxy usa os canais de plugin `BungeeCord` e `mCore`.

## Como o projeto funciona

O mCore é carregado como plugin no servidor ou no proxy, inicializa a camada de configuração e banco de dados e registra os serviços correspondentes ao ambiente. O restante dos plugins da rede usa essas APIs em vez de implementar novamente persistência, perfis ou transferência de jogadores.

No Spigot, a inicialização verifica primeiro a versão NMS. Em seguida carrega configurações, registra placeholders, menus, banco, cargos, NPCs, hologramas, comandos, listeners e canais de plugin. O perfil de cada jogador é carregado quando necessário, mantido em memória durante a sessão e salvo quando sofre alterações e nos fluxos de saída/transferência.

No BungeeCord, o ponto de entrada carrega configuração, conexão MySQL, cargos, comandos e listeners do proxy. A classe `Manager` fornece uma fachada comum para operações que precisam funcionar tanto com objetos Bukkit quanto com jogadores BungeeCord.

A arquitetura completa está em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Arquitetura e persistência

| Componente | Responsabilidade |
| --- | --- |
| `Core` | Inicialização e ciclo de vida no Spigot/Bukkit |
| `Bungee` | Inicialização e ciclo de vida no BungeeCord |
| `Manager` | Fachada para diferenças entre Bukkit e BungeeCord |
| `Profile` | Estado de um jogador durante a sessão |
| `Database` / `HikariDatabase` | Contrato de persistência e implementação MySQL |
| `DataTable` e `DataContainer` | Mapeamento de tabelas e campos do perfil |
| `SchemaMigrator` | Evolução incremental do schema MySQL |
| `Queue` | Salvamento e transferência ordenada de jogadores |

O writer de banco usa uma fila serializada e limitada para as gravações de perfil. Falhas de escrita são retidas para nova tentativa, inclusive por arquivos em `plugins/mCore/pending-saves`. Esse mecanismo reduz perda de alterações, mas não substitui backups do banco nem monitoramento dos erros.

## Tecnologias e dependências

| Tecnologia | Uso |
| --- | --- |
| Java 8 | Bytecode e APIs compatíveis com a stack legada |
| Spigot 1.8.8 | API Bukkit e implementação NMS `v1_8_R3` |
| BungeeCord API 1.8 | Ponto de entrada e integração com o proxy |
| MySQL | Persistência dos perfis, estatísticas e dados de rede |
| HikariCP 3.4.5 | Pool de conexões JDBC |
| MySQL Connector/J 8.0.33 | Driver JDBC |
| ProtocolLib 4.5.0 | Pacotes, NPCs e hologramas no ambiente Bukkit |
| PlaceholderAPI 2.10.5 | Placeholders e expansão do mCore |
| Gradle Kotlin DSL | Build principal da branch de refatoração |
| GitHub Actions | Build de validação e testes do importador |

As versões e escopos exatos estão em `build.gradle.kts`. Dependências declaradas como `compileOnly` não são incorporadas ao JAR e precisam existir no servidor correspondente.

## Compilar

O caminho de build atual é Gradle:

```bash
./gradlew --no-daemon clean build
```

O artefato esperado é:

```text
build/libs/mCore.jar
```

Em pushes para `main`, o GitHub Actions também copia o JAR validado para [`dist/mCore.jar`](dist/mCore.jar) e publica seu checksum em [`dist/mCore.jar.sha256`](dist/mCore.jar.sha256).

O build necessita de JDK 8 e dos artefatos compatíveis de Spigot 1.8.8/NMS gerados pelo BuildTools. Em servidores Bukkit, instale também ProtocolLib 4.5.0 e PlaceholderAPI 2.10.5. O `pom.xml` foi mantido temporariamente como referência de migração e não é o caminho de build recomendado.

Consulte [docs/BUILD.md](docs/BUILD.md) antes de compilar ou publicar uma versão.

## Configurar o MySQL

O arquivo padrão fica em `src/main/resources/config.yml` e é copiado para `plugins/mCore/config.yml` na primeira inicialização. Configure host, porta, database e usuário no ambiente de destino. A senha pode ser fornecida pela variável de ambiente `MCORE_MYSQL_PASSWORD`, que tem precedência sobre o valor configurado.

A conexão de runtime foi preparada para TLS com verificação de certificado e hostname (`sslMode=VERIFY_IDENTITY`). Para usar a CA fornecida pelo provedor, consulte o procedimento de [build e configuração](docs/BUILD.md). Não desabilite a validação TLS para contornar erros de certificado.

## Migração de dados

A branch inclui uma ferramenta offline para importar exports JSONL de três coleções MongoDB legadas para o schema MySQL do mCore. A ferramenta não é empacotada no plugin e não conecta diretamente ao MongoDB. Ela usa os arquivos exportados pelo `mongoexport`, valida o schema de destino e permite uma execução inicial em modo `--dry-run`.

O procedimento exige backup de origem e destino, revisão do mapeamento e validação funcional em homologação. Leia [docs/DATA-MIGRATION.md](docs/DATA-MIGRATION.md) antes de importar qualquer dado real.

## Documentação

| Documento | Conteúdo |
| --- | --- |
| [Arquitetura](docs/ARCHITECTURE.md) | Ciclo de vida Bukkit/Bungee, módulos, perfil, banco e comunicação |
| [Build](docs/BUILD.md) | JDK, Gradle, Spigot 1.8.8, dependências e MySQL TLS |
| [Migração de dados](docs/DATA-MIGRATION.md) | Procedimento MongoDB JSONL para MySQL |
| [Segurança](docs/SECURITY.md) | Segredos, TLS, privilégios, persistência e limites de segurança |
| [Estado da implementação](docs/EXECUTION-STATUS.md) | O que está implementado, validações e pendências conhecidas |

## Estado e limites conhecidos

O repositório contém alterações de uma refatoração de persistência e build. A presença de um workflow não prova que todas as combinações de ambiente tenham sido testadas. A integração com Spigot 1.8.8, NMS, BungeeCord, MySQL real e os dados migrados precisa ser validada em homologação antes de produção.

A documentação de [estado da implementação](docs/EXECUTION-STATUS.md) separa o que está implementado do que ainda exige verificação. Não trate a migração como concluída apenas porque o script terminou sem erro.

## Licença

Distribuído sob a licença MIT. Consulte [LICENSE](LICENSE).
