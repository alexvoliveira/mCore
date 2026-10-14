# mCore — estado da migração

A branch `refactor/jdk21-spigot-1.21.1` foi criada sobre `refactor/stage1-security-gradle`, preservando as alterações de segurança e build já existentes. Esta migração permanece em validação e **não deve ser implantada em produção até os testes do servidor terminarem**.

## Implementado nesta branch

- Build Gradle Kotlin DSL com toolchain JDK 21 e `options.release = 21`.
- Dependência de compilação direcionada à API Bukkit/Spigot 1.21.1; o projeto não precisa compilar o servidor CraftBukkit completo para a camada de compatibilidade nova.
- Fonte legado `nms/v1_8_R3` excluído da compilação ativa.
- Detecção da versão atualizada para funcionar com `Server#getBukkitVersion()` quando a implementação CraftBukkit não fornece pacote versionado.
- Camada de compatibilidade Bukkit para armor stands, itens, slimes, títulos, action bar, tablist e eventos de holograma.
- Primeira implementação de NPCs de cliente com ProtocolLib, incluindo perfil/skin, tablist e uma movimentação simples pelo Bukkit.
- Removida a exigência rígida de PlaceholderAPI 2.10.5. O plugin declara dependência de ProtocolLib e PlaceholderAPI no `plugin.yml`.
- Workflows de validação e release migrados para JDK 21.
- As configurações MySQL/TLS e a refatoração de persistência preexistentes foram preservadas.

## Limites e validação pendente

- O workflow do GitHub Actions está sendo usado para executar o build. Um job em andamento ou cancelado não é evidência de compilação aprovada; registre aqui o resultado final após o pipeline concluir.
- Os NPCs agora usam uma entidade Bukkit como suporte e ProtocolLib para mostrá-la como jogador no cliente. A movimentação é simplificada e não equivale ao pathfinding interno de NMS.
- Recursos como animações, step height, injeção no mundo, tablist, skins e refresh de jogador que antes dependiam de NMS privado precisam de testes funcionais específicos.
- O repositório `alexvoliveira/Spigot-1.21.1` fornece API Bukkit, não o código interno CraftBukkit/NMS. Não se deve afirmar compatibilidade binária com internals da versão a partir dessa source.
- A fork atual de ProtocolLib está configurada para Minecraft 26.2 e JDK 25; ela não é usada automaticamente nesta branch. A dependência de compilação foi ajustada para ProtocolLib 5.3.0, release da linha 1.21 que precisa ser validada no runtime 1.21.1. PlaceholderAPI permanece em 2.12.3, versão estável compatível com o contrato usado pelo mCore; a fork de desenvolvimento não é necessária para a expansão atual.
- `pom.xml` continua temporariamente para referência até o build Gradle estar aprovado. Ele ainda contém dados antigos de Spigot 1.8.8, PAPI/ProtocolLib antigos e caminho absoluto local; não use Maven para produzir o JAR desta branch.

## Checklist antes de produção

1. Obter `BUILD SUCCESSFUL` no workflow para o HEAD atual da branch.
2. Confirmar a existência de `build/libs/mCore.jar`.
3. Testar inicialização/desligamento com Spigot 1.21.1, ProtocolLib e PlaceholderAPI instalados.
4. Testar NPCs com e sem skin, navegação, entrada/saída de jogadores, tablist, interação e remoção.
5. Testar hologramas de texto, toque e item, incluindo unload/load de chunks e prevenção de coleta.
6. Testar o banco, TLS Aiven, persistência de perfis e o plugin Bungee separado.
7. Só após isso remover o `pom.xml` legado e realizar revisão final das referências de API antiga.

A branch ainda não foi mesclada a `main`.
