# Arquitetura do mCore

Este documento descreve a organização encontrada no código, com foco nos dois ambientes de execução (Spigot/Bukkit e BungeeCord), no ciclo de vida dos perfis e na persistência MySQL.

## 1. Visão geral

O mCore é uma biblioteca de funcionalidades distribuída como plugin. Ele concentra serviços utilizados por outros componentes da rede, especialmente plugins Bukkit que precisam acessar perfis, menus, NPCs, hologramas, placeholders e transferência de jogadores.

O artefato contém duas classes de entrada:

- `tk.slicecollections.maxteer.Core`: plugin Bukkit/Spigot.
- `tk.slicecollections.maxteer.bungee.Bungee`: plugin BungeeCord.

O código compartilhado usa `Manager` para adaptar operações entre tipos de jogador e APIs de cada plataforma. A seleção do ambiente é detectada pela presença das classes da API BungeeCord.

```text
                Rede Minecraft
                       |
          +------------+------------+
          |                         |
     Proxy BungeeCord          Servidores Spigot
          |                         |
      Bungee.java                Core.java
          |                         |
          +-----------+-------------+
                      |
                Serviços mCore
                      |
        +-------------+--------------+
        |             |              |
     Profile       Features       Plugin channels
        |
   Database / HikariDatabase
        |
      MySQL
```

## 2. Pontos de entrada e ciclo de vida

### Spigot/Bukkit: `Core`

A classe `Core` estende `MPlugin`. No `start()`, registra a instância. O `enable()` executa as etapas principais nesta ordem:

1. Verifica a versão NMS por `NMS.setupNMS()`. A implementação exige a linha Spigot 1.8 / NMS `v1_8_R3`; se não for compatível, o plugin se desativa.
2. Carrega as configurações padrão, define spawn, raio de proteção e permissão da fila VIP.
3. Verifica a blacklist de plugins incompatíveis. Se houver conflito configurado, interrompe a inicialização.
4. Registra a expansão do PlaceholderAPI e os listeners/schedulers de inventário.
5. Inicializa a conexão MySQL e as tabelas do perfil.
6. Registra tarefa de limpeza de perfis pré-carregados.
7. Inicializa NPCs, hologramas, cargos, fake players, títulos, boosters, entregas, servidores e conquistas.
8. Registra comandos, listeners, adaptadores ProtocolLib e canais de plugin `BungeeCord` e `mCore`.
9. Marca `Core.validInit` como verdadeiro.

O mLobby usa essa flag para confirmar que o Core está pronto antes de inicializar suas próprias funções.

No `disable()`, a classe cancela tarefas, remove listeners de pacotes e expansão PlaceholderAPI, encerra filas/parties/títulos/listeners, destrói NPCs e hologramas, salva perfis retidos e fecha o pool de conexões. O encerramento tenta persistir perfis e registra falhas que não conseguiu resolver.

### BungeeCord: `Bungee`

O ponto de entrada do proxy estende `Plugin`. No `onEnable()`, carrega `config.yml`, inicializa o mesmo contrato de banco, carrega os cargos, registra comandos/listeners e registra o canal `mCore`.

No `onDisable()`, encerra parties e callbacks de listeners, limpa caches e fecha a conexão com o banco. Essa parte do plugin trabalha diretamente com classes da API BungeeCord e não inicializa NMS Bukkit.

### Fachada multiplataforma: `Manager`

`Manager` centraliza chamadas que variam entre Bukkit e BungeeCord, como obter jogador/nome, verificar permissões, enviar mensagens e tratar fake players. A implementação usa reflexão para adaptar as classes carregadas no ambiente atual.

Essa fachada permite compartilhar parte do domínio sem transformar as APIs específicas de cada plataforma em dependências de todos os módulos de negócio. Ainda assim, chamadas reflexivas dependem da existência e compatibilidade dos métodos esperados.

## 3. Perfis de jogador

`Profile` representa os dados e o estado de um jogador durante a sessão. Na criação, o perfil solicita ao banco as linhas correspondentes ao nome do jogador e mantém os valores em um mapa de `DataContainer`.

O perfil concentra, entre outros elementos:

- dados básicos e cargo;
- estatísticas de SkyWars, The Bridge e Murder;
- preferências e seleção de elementos visuais;
- títulos, conquistas, entregas e boosters;
- hotbar, scoreboard e estado da partida;
- estado temporário de combate e informações usadas na sessão.

O carregamento do perfil acontece sob demanda a partir dos fluxos de login/entrada de jogador. A tarefa de limpeza remove perfis pré-carregados que não se tornaram sessões ativas, evitando mantê-los indefinidamente na memória.

Quando o jogador entra no lobby, o perfil pode restaurar estado visual e inventário, aplicar hotbar, atualizar visibilidade e posicionar o jogador no spawn configurado. Ao entrar em uma partida, o vínculo de jogo muda e os elementos de lobby são tratados de maneira diferente.

## 4. Persistência MySQL

### Contrato e implementação

`Database` é o contrato utilizado pelo domínio. A implementação suportada pela branch é `HikariDatabase`, baseada em HikariCP e MySQL Connector/J.

Na inicialização, o banco:

1. abre o pool de conexões;
2. cria as tabelas declaradas por `DataTable`;
3. executa `SchemaMigrator`;
4. recupera snapshots de perfil pendentes em disco.

As tabelas de perfil declaradas são:

| Tabela | Domínio |
| --- | --- |
| `mCoreProfile` | Perfil geral, cargo, cash, preferências, títulos, entregas e dados comuns |
| `mCoreSkyWars` | Estatísticas e configurações do SkyWars |
| `mCoreTheBridge` | Estatísticas e configurações do The Bridge |
| `mCoreMurder` | Estatísticas e configurações do Murder |

O backend também mantém `mCoreNetworkBooster` e `mCoreTitles`. A tabela `mCoreSchemaHistory` registra as migrações incrementais conhecidas pelo código.

### Leitura e gravação

Na leitura, cada tabela consulta a linha pelo nome do jogador. Quando a linha ainda não existe, os valores padrão são inseridos e lidos novamente. Se dois processos criarem o mesmo perfil ao mesmo tempo, o código tenta reler a linha depois do conflito de inserção.

Os campos são mantidos em `DataContainer`, que acompanha se um valor foi alterado. Na gravação, o banco captura um snapshot dos campos modificados e executa os updates dentro de uma transação. Os nomes de tabela/coluna usados no fluxo de leaderboard são validados contra os schemas conhecidos por `SqlIdentifier`.

### Escritas assíncronas e recuperação

A implementação usa um writer de thread único com fila limitada. Isso mantém a ordem de submissão de gravações e impede que várias operações de perfil concorram indiscriminadamente no mesmo executor.

Quando a gravação falha, o snapshot é retido em memória e pode ser salvo em `plugins/mCore/pending-saves` para tentativa posterior. A fila é drenada no encerramento por um período limitado; se não for possível finalizar, o log informa que há dados pendentes.

Esse mecanismo é uma proteção operacional, não uma garantia absoluta contra falhas de hardware ou perda do banco. Monitore os logs, o diretório de snapshots pendentes e mantenha backups MySQL testados.

## 5. Migrações do schema

`SchemaMigrator` executa alterações idempotentes identificadas por versão. Ele usa um lock MySQL para impedir que instâncias Bukkit e Bungee tentem aplicar a mesma migração simultaneamente.

Cada versão verifica o que precisa existir, aplica as alterações e grava a descrição em `mCoreSchemaHistory`. O mecanismo atual cobre as alterações listadas no próprio migrator; não substitui um inventário de todas as modificações históricas eventualmente aplicadas manualmente em bancos antigos.

## 6. Fila e transferência de servidores

`Core.sendServer(profile, name)` identifica a fila apropriada (VIP ou membro), evita duplicar a solicitação e adiciona o jogador à fila do servidor de destino.

O fluxo da fila é executado no thread principal do Bukkit. Para o jogador no início da fila:

1. solicita `profile.saveAsync()`;
2. aguarda a conclusão do future;
3. em caso de sucesso, envia a mensagem de conexão pelo canal `BungeeCord`;
4. em caso de falha, cancela a transferência e informa o jogador.

A fila remove jogadores desconectados e limpa seu estado quando termina. Esse comportamento é essencial: a transferência não deve ser tratada como um simples envio de plugin message, porque a persistência do perfil faz parte do contrato.

## 7. Serviços de domínio

Os módulos em `src/main/java/tk/slicecollections/maxteer` podem ser agrupados por responsabilidade:

| Pacote | Responsabilidade |
| --- | --- |
| `player` | Perfil, fake players, cargos, hotbar e scoreboard |
| `database` | Persistência, cache de cargos, containers e schema |
| `queue` | Filas e conexão com servidores |
| `bukkit` / `bungee` | Adaptadores e recursos específicos de cada plataforma |
| `party` e integrações | Party/compatibilidade com plugins externos |
| `achievements` | Conquistas e estatísticas relacionadas |
| `booster` | Boosters individuais e de rede |
| `deliveries` | Recompensas e entregas |
| `titles` | Títulos e seleção visual |
| `menus` | Inventários de perfil, servidores e funcionalidades |
| `libraries/npclib` | NPCs, traits e eventos |
| `libraries/holograms` | Hologramas e linhas de texto |
| `nms` | Abstrações e implementação específica de NMS |
| `hook` / `listeners` | ProtocolLib, PlaceholderAPI e eventos |

A camada de domínio compartilha modelos quando possível; as classes que precisam tocar diretamente em Bukkit, BungeeCord ou NMS permanecem específicas.

## 8. Configuração e integrações

Os recursos padrão em `src/main/resources` são copiados para `plugins/mCore/` nas instalações novas. Entre os arquivos estão:

- `config.yml`: conexão MySQL;
- `core.yml`: spawn, fila, limpeza de perfil e intervalos;
- `roles.yml`: cargos e permissões;
- `utils.yml`: opções auxiliares;
- `deliveries.yml`, `titles.yml` e demais configurações de recursos.

O arquivo `plugin.yml` é o descritor Bukkit e o `bungee.yml` é o descritor do proxy. O Gradle processa esses arquivos para inserir nome, versão e classes principais a partir das propriedades do build.

## 9. Limites arquiteturais

- A implementação Bukkit está presa a Spigot 1.8.8/NMS `v1_8_R3`; uma atualização de versão exige revisar o pacote NMS e os adaptadores de pacotes.
- A API BungeeCord é da geração 1.8 e também exige validação quando o proxy for atualizado.
- O estado de sessão e caches são locais ao processo; MySQL é o mecanismo de persistência compartilhado.
- A fila de gravação é serializada e limitada. Sob alta carga, a aplicação pode rejeitar submissões quando a fila enche; isso deve ser monitorado.
- A documentação descreve a estrutura atual do código, mas não substitui testes de integração no ambiente real da rede.

