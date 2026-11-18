# Estado da implementação

Este documento registra o que foi encontrado no código e separa implementação de validação operacional. A presença de uma classe, script ou workflow não significa que o fluxo completo foi aprovado em produção.

## Implementado na branch consolidada

### Persistência
- O caminho de persistência usado pelo código de produção é MySQL por HikariCP.
- O contrato comum está em `Database`; a implementação concreta está em `HikariDatabase`.
- O schema é construído a partir das classes `DataTable` e evoluído por `SchemaMigrator`.
- O perfil é salvo por snapshots dos campos alterados, em transações.
- As gravações assíncronas usam um writer serializado e uma fila limitada.
- Falhas de gravação podem ser retidas para nova tentativa, inclusive por arquivos em `plugins/mCore/pending-saves`.

### Segurança e ciclo de vida
- A conexão JDBC usa validação TLS de certificado e hostname.
- A senha pode vir da variável `MCORE_MYSQL_PASSWORD`.
- A inicialização Bukkit valida a versão NMS e encerra o plugin se a versão não for compatível.
- A instalação automática de JARs não confiáveis e a finalização do processo via `System.exit(0)` foram removidas do fluxo descrito pela refatoração.
- O encerramento tenta salvar perfis ativos e retidos, fechar filas, remover listeners e encerrar conexões.

### Build e migração
- Há configuração Gradle Kotlin DSL e Wrapper.
- O workflow configura Java 8, prepara Spigot 1.8.8, executa os testes Python do importador e compila o JAR.
- O importador offline recebe exports JSONL do MongoDB e valida o schema MySQL antes da escrita.
- A documentação do build, da migração e da arquitetura está versionada.

## O que ainda precisa ser validado

### Build e dependências
- Consultar o resultado real do workflow na revisão que será publicada. A existência do workflow não comprova que o build passou.
- Executar `./gradlew --no-daemon clean build` em ambiente com JDK 8 e artefatos corretos do Spigot 1.8.8/NMS.
- Confirmar que a API BungeeCord usada pelo código continua compatível com a versão de proxy do ambiente.
- Validar o JAR gerado em servidores Bukkit e BungeeCord separados.

### Banco e gravação
- Testar carga, saturação da fila de escrita, rejeição de tarefas, encerramento durante gravações e recuperação dos snapshots pendentes.
- Verificar permissões reais do usuário MySQL e o comportamento de TLS com o certificado/hostname do ambiente.
- Confirmar as migrações em uma cópia do schema de produção antes de conectar servidores.

### Migração de dados
- Executar `--dry-run` com exports representativos.
- Comparar contagens e valores de uma amostra de perfis.
- Testar a importação em homologação, incluindo recuperação após falhas parciais.
- Validar os fluxos do jogo usando os dados importados.

### Integração funcional
- Testar entrada/saída e carregamento de perfis.
- Testar salvamento, fila VIP/membro e transferência de servidor.
- Testar comandos, cargos, fake players, parties, NPCs, hologramas, hotbar, scoreboards, títulos, entregas, boosters e conquistas.
- Testar o fluxo Bukkit ↔ BungeeCord com as versões exatas do proxy e plugins externos instalados.

## Pendências conhecidas

1. O `pom.xml` histórico continua no repositório como referência e deve ser removido somente depois que o build Gradle e o processo de release estiverem validados.
2. A revisão de concorrência/perda de atualização de snapshots de perfil precisa de testes de estresse e integração.
3. A migração do banco real exige procedimento operacional, backup e reconciliação de dados; não é coberta apenas por testes unitários do script.
4. A implementação permanece dependente de Spigot 1.8.8, NMS `v1_8_R3` e APIs antigas de BungeeCord/ProtocolLib/PlaceholderAPI.
5. A revisão deste documento não é pentest, auditoria formal, nem garantia de ausência de defeitos.

## Critério para considerar pronto

A refatoração só deve ser considerada pronta para produção depois de:

- workflow de build/teste aprovado no commit final;
- instalação de homologação validada nos dois ambientes (Bukkit e BungeeCord);
- migração de dados reconciliada e aprovada;
- testes funcionais e de encerramento aprovados;
- monitoramento de erros de banco e diretório de snapshots pendentes configurados;
- backup e plano de rollback testados.

