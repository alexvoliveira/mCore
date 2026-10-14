# Análise técnica — JDK 21 / Spigot 1.21.1

## Escopo e ponto de partida

Esta migração parte de `refactor/stage1-security-gradle`, não de `main`, para preservar o hardening de segurança, o caminho Gradle, HikariCP, o importador offline JSONL e a migração anterior de menus para `inventory-api`.

O mCore não é apenas uma biblioteca Bukkit: o artefato também declara um entry point BungeeCord (`tk.slicecollections.maxteer.bungee.Bungee`) e contém um subsistema próprio de NPCs, hologramas, skins, packets, menus, perfis e persistência. Por isso, o alvo Java/Spigot é só uma parte da compatibilidade.

## Subsistemas identificados

### Bootstrap e ciclo de vida

- `Core` organiza o bootstrap Bukkit, registra dependências, listeners de pacotes e canais plugin messaging, registra a expansão PlaceholderAPI, carrega configurações e inicia o banco.
- `MPlugin` centraliza o ciclo `onLoad/onEnable/onDisable`, logger e configuração.
- `bungee/Bungee` fornece a entrada do proxy e o lado BungeeCord da mesma biblioteca.
- O build Gradle filtra `plugin.yml` e `bungee.yml` para preencher nome, versão e classes main.

### PlaceholderAPI

- `MCoreExpansion` fornece placeholders de perfil, visibilidade, cargo, servidores e estatísticas.
- O código verificava exatamente a versão `2.10.5` em tempo de execução. Essa condição foi removida: a dependência obrigatória declarada em `plugin.yml` garante a presença, e uma igualdade exata de versão impediria atualizações compatíveis sem necessidade.
- A compatibilidade da expansão precisa ser confirmada no build e em teste de servidor com PlaceholderAPI 2.12.3.

### ProtocolLib e protocolo de rede

- `FakeAdapter` altera mensagens/comandos e entradas de scoreboard para o sistema de nick falso.
- `NPCAdapter` processa pacotes de informação de jogadores e propriedades de perfil para NPCs/skins.
- `HologramAdapter` filtra spawn/metadata de entidades de holograma segundo quem pode vê-las.
- Os wrappers legados em `com/comphenix/packetwrapper` e os métodos de `NMS` dependem de layouts e índices de campos de pacotes que mudaram no protocolo moderno. A compilação contra ProtocolLib 5.4.0 é necessária, mas não substitui teste funcional dos tipos de pacote.

### NMS, NPCs e hologramas: bloqueio principal

- `NMS` é a fachada usada pelos demais componentes, enquanto `INMS` define seu contrato interno.
- `NMS1_8R3` implementa o contrato usando classes obsoletas de CraftBukkit/NMS.
- O pacote `nms/v1_8_R3` tem 20 arquivos que acessam diretamente `net.minecraft.server.v1_8_R3`, CraftWorld/CraftPlayer/CraftEntity versionados, o tracker e campos privados internos.
- `EntityNPCPlayer` deriva do antigo `EntityPlayer`; implementa NPC fake como Player Bukkit, skin, movimento, navegação e handlers/controllers próprios. Isso não pode ser substituído por uma simples entidade Bukkit sem alterar contratos externos.
- `HumanController` cria esse jogador fake. `AbstractNPC` pressupõe que o controller disponibilize uma entidade válida, chama inserção/remoção de mundo, aplica metadados e invoca a camada NMS para yaw, tracker e skin.
- `HologramLine` usa `IArmorStand`, `ISlime` e `IItem` para desenhar linhas, interações e pickups; o NMS antigo implementa bounding boxes e controle de entidades fora do que a API Bukkit expõe.
- `SkinPacketTracker` e `SkinUpdateTracker` dependem da representação de NPC como Player/SkinnableEntity e de pacotes de player list. Retornar `null`, criar um armor stand no lugar do jogador ou desabilitar os métodos silenciosamente não preservaria o comportamento esperado.

## Origem Spigot fornecida

O repositório `alexvoliveira/Spigot-1.21.1` contém a árvore de código da **API Bukkit/Spigot** e o POM `org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT`. A árvore examinada não contém `org.bukkit.craftbukkit` nem `net.minecraft`. Ela é apropriada para conferir assinaturas públicas, mas não fornece as classes internas que a implementação NMS 1.8 usa.

## Decisões de dependências

- Spigot API: `org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT`.
- BungeeCord API: `net.md-5:bungeecord-api:1.21-R0.1`; foi necessário adicionar `https://libraries.minecraft.net/` para a dependência transitiva `com.mojang:brigadier:1.2.9`.
- ProtocolLib: `net.dmulloy2:ProtocolLib:5.4.0`, estável, coordenadas atuais e baseline Java 17. A fork própria está em `5.5.0-SNAPSHOT`, mirando Minecraft `26.2` e Java 25, incompatível como dependência direta declarada para esta linha 1.21.1/Java 21.
- PlaceholderAPI: `me.clip:placeholderapi:2.12.3`, estável. A fork própria `main` declara uma versão `2.12.4-DEV` e inclui fontes Paper/Folia de uma linha API mais nova, então não foi tratada como versão publicada/pinável do mCore.

Essas versões devem ser revistas se as forks publicarem versões específicas para Minecraft 1.21.1 e Java 21, com tags/artefatos verificáveis.

## Resultado atual

O trabalho de build e configurações está em andamento e o CI está sendo usado para confirmar resolução de dependências e erros de compilação. A camada NMS não foi portada ainda. Portanto, o plugin continua deliberadamente não pronto para implantação: `NMS.setupNMS()` aceita apenas a implementação `v1_8_R3` e `Core.enable()` desativa o plugin quando não encontra um adaptador. Essa falha explícita é preferível a permitir iniciar com métodos NMS ausentes e quebrar NPCs/hologramas durante o uso.
