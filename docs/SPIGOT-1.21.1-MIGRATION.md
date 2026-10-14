# Migração para JDK 21 e Spigot 1.21.1

## Estado

Esta branch contém a migração de build e uma primeira camada de compatibilidade para o ciclo de vida de hologramas e NPCs. A validação depende do workflow `.github/workflows/build.yml` e de testes funcionais no servidor. Não declarar a migração concluída apenas porque o Gradle compilou.

## Decisões técnicas

- Gradle Wrapper 8.14.3 com toolchain Java 21 e `options.release = 21`.
- Compilação contra Spigot 1.21.1; os fontes que herdam de classes internas `net.minecraft.server.v1_8_R3` e `org.bukkit.craftbukkit.v1_8_R3` são excluídos do source set ativo.
- A camada NMS moderna usa as interfaces Bukkit públicas e ProtocolLib para operações de pacote que não existem na API Bukkit.
- A detecção de versão usa a versão Bukkit quando o nome do pacote CraftBukkit não é versionado.
- A checagem rígida de PlaceholderAPI 2.10.5 foi removida. O contrato de expansão usado pelo mCore não precisa dessa versão exata.
- As dependências de compilação são ProtocolLib 5.3.0 e PlaceholderAPI 2.12.3. A fork ProtocolLib do proprietário atualmente declara `mcVersion=26.2` e usa toolchain JDK 25; a fork PlaceholderAPI está em linha de desenvolvimento 2.12.4-DEV. Nenhuma das forks deve ser adotada apenas por ter uma versão mais recente: primeiro é necessário construir para 1.21.1/JDK 21 e testar o runtime.

## Limites que precisam de teste

A source vinculada de Spigot 1.21.1 fornece a API Bukkit, mas não as classes internas CraftBukkit/NMS. Por isso, não é possível manter literalmente subclasses de entidades internas de 1.8 usando apenas essa source.

O controlador de NPC desta branch usa uma entidade Bukkit como suporte de navegação e ProtocolLib para representá-la como jogador no cliente. Embora a interface pública de NPC retorne `Entity`, código consumidor que faça cast do retorno para `Player` ou dependa de uma conexão de jogador interna precisa ser revisado. Skin deve ser configurada pela API `NPC.setSkin(Skin)`.

A API Bukkit não expõe algumas operações internas usadas pela implementação 1.8, como substituir entradas privadas do tracker ou alterar diretamente o step height de uma entidade. O comportamento de navegação, animação, skins, tablist, interação, remoção e respawn precisa ser testado separadamente antes de substituir o JAR em produção.

## Verificações antes do deploy

1. Confirmar que o workflow gera `build/libs/mCore.jar` em JDK 21.
2. Testar inicialização e desligamento no Spigot 1.21.1 com as versões efetivamente instaladas de ProtocolLib e PlaceholderAPI.
3. Criar NPCs com e sem skin; testar entrada/saída de jogadores, teleporte, navegação, interação, visibilidade de tablist e remoção.
4. Criar hologramas de texto, toque e item; testar movimento, respawn após descarregar chunks e prevenção de coleta de itens decorativos.
5. Testar banco de dados e persistência de perfis separadamente da migração de API.

## Revisão das APIs legadas

- A varredura da árvore ativa encontrou 192 arquivos Java no source set (os 20 arquivos em `nms/v1_8_R3` ficam excluídos). Não há imports diretos de `net.minecraft.*` ou `org.bukkit.craftbukkit.*` nesses arquivos ativos; alguns utilitários ainda mantêm reflexão por nome de classe e não fazem parte do caminho principal da ponte `NMS1_21_R1`.
- `ParticleEffect.ParticlePacket` foi migrado de construção/reflexão de pacotes NMS para `Player.spawnParticle` do Bukkit. Isso elimina a dependência de nomes internos versionados para o envio de partículas. Os nomes antigos que não possuem equivalência pública direta (`FOOTSTEP` e `ITEM_TAKE`) são tratados como não suportados; partículas de bloco/item usam os tipos públicos modernos, então o byte de dados legado não é preservado universalmente.
- `BukkitUtils.asNMSCopy`, `BukkitUtils.asCraftMirror`, `MinecraftReflection` e `ReflectionUtils.PackageType` ainda representam utilitários de compatibilidade legado. Se algum consumidor chamar essas rotinas diretamente, a ausência de um pacote CraftBukkit versionado pode torná-las incompatíveis. O caminho atual de `NMS1_21_R1` não deve passar por elas.
- As interfaces públicas `INMS`, `IArmorStand`, `IItem` e `ISlime` foram mantidas. Algumas operações que exigiam acesso a internals — substituição do tracker, step height e lógica de movimento de voo — não têm implementação equivalente na API Bukkit pública e permanecem sem efeito na ponte nova.
- O CI valida compilação, empacotamento e importador de dados; ele não substitui testes em servidor real. Em particular, NPCs, skins, pacotes de tablist, colisões/navegação, hologramas e unload/reload de chunks ainda precisam de smoke tests com Spigot 1.21.1 e o JAR exato de ProtocolLib instalado.

