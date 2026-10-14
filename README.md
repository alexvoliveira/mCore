# mCore

Biblioteca de funções compartilhadas para os plugins da rede SliceCollections.

## Plataforma desta branch

Esta branch migra o build do mCore para **JDK 21 e Spigot 1.21.1**. O artefato de produção é gerado pelo Gradle Wrapper com `build/libs/mCore.jar`.

- Branch de migração: `refactor/jdk21-spigot-1.21.1`
- Compatibilidade de API: Spigot 1.21.1
- Dependências de integração: ProtocolLib e PlaceholderAPI
- A implementação NMS antiga de 1.8 foi isolada da compilação; a camada atual utiliza Bukkit e ProtocolLib.

A migração deve ser considerada validada somente após o workflow do GitHub Actions compilar o artefato e após testes em um servidor de teste com as versões reais das dependências. A conclusão do build, isoladamente, não substitui os testes de NPCs, skins, hologramas, banco de dados e desligamento.
