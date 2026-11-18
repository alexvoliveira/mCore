# Segurança e operação segura

Este documento registra medidas presentes no código e requisitos operacionais para executar o mCore. Ele não é uma certificação de segurança nem substitui revisão de código, pentest ou hardening do servidor Minecraft.

## 1. Banco e credenciais

O plugin lê host, porta, schema e usuário MySQL de `plugins/mCore/config.yml`. A senha pode ser fornecida por `MCORE_MYSQL_PASSWORD`, que tem precedência sobre o campo de senha do YAML.

- Não versione senhas, URLs JDBC com credenciais ou variáveis exportadas.
- Use um usuário MySQL dedicado ao mCore.
- Restrinja acesso de rede ao banco apenas aos hosts Bukkit/Bungee que precisam dele.
- Conceda privilégios somente no schema do mCore.
- Não compartilhe uma conta administrativa MySQL com outros serviços.
- Rotacione imediatamente qualquer senha que tenha sido publicada.

Os valores padrão versionados devem ser placeholders locais, nunca endpoints reais de produção.

## 2. TLS

A conexão JDBC é configurada com `sslMode=VERIFY_IDENTITY`. Isso requer uma cadeia de certificados válida e hostname correspondente ao certificado apresentado pelo servidor.

Para configurar uma CA PEM no runtime Java:
- salve o arquivo em `plugins/mCore/ca.pem`, ou
- defina `MCORE_MYSQL_CA_CERT` para o caminho absoluto.

O truststore PKCS12 anterior continua suportado via `MCORE_MYSQL_TRUSTSTORE` e `MCORE_MYSQL_TRUSTSTORE_PASSWORD`. A presença do PEM tem precedência.

Não use `sslMode=DISABLED`, truststore permissivo ou certificado arbitrário para contornar falha de handshake. Corrija CA, DNS, cadeia e validade do certificado.

## 3. SQL e dados

As consultas de perfil usam parâmetros para os valores recebidos. O módulo `SqlIdentifier` valida os nomes de tabelas/colunas usados na consulta de leaderboard contra as estruturas declaradas no mCore. Preserve essas validações ao adicionar novos relatórios ou campos.

Isso não significa que toda consulta futura esteja automaticamente protegida. Ao adicionar SQL:
- use `PreparedStatement` para valores;
- não concatene entrada de jogador em comandos SQL;
- valide identificadores contra uma allowlist;
- trate erros sem imprimir senhas, tokens ou strings de conexão;
- revise o escopo e as permissões do usuário do banco.

## 4. Persistência, fila e snapshots

As gravações de perfil passam por um executor serializado com fila limitada. Quando uma gravação falha, o mCore tenta reter o snapshot e persistir uma cópia em `plugins/mCore/pending-saves` para nova tentativa.

Operação segura exige:
- manter esse diretório gravável pelo processo Java;
- monitorar logs de falha e avisos de snapshots retidos;
- não apagar arquivos pendentes sem investigação;
- manter backups MySQL consistentes;
- testar restauração e recuperação antes de atualização de schema.

Snapshots locais não substituem backup, replicação nem estratégia de recuperação de desastre.

## 5. Plugins, JARs e dependências

Instale mCore, ProtocolLib e PlaceholderAPI de fontes confiáveis. Valide o hash dos artefatos publicados e restrinja as permissões do usuário do sistema operacional que pode escrever em `plugins/`.

O mCore exige ProtocolLib 4.5.0 e PlaceholderAPI 2.10.5 no ambiente Bukkit conforme o descritor e as verificações de runtime. Não substitua essas dependências por versões arbitrárias sem testes.

A inicialização usa reflexão e NMS. Isso aumenta a sensibilidade à versão de servidor e torna importante testar toda atualização em uma cópia do ambiente real.

O fluxo automático de instalação de JARs arbitrários foi removido da refatoração; não reintroduza atualização executável sem validação de origem, integridade e assinatura.

## 6. Permissões e operação do servidor

- Restrinja comandos administrativos do mCore a operadores confiáveis.
- Revise as permissões definidas em `roles.yml` e `core.yml`.
- Configure a permissão de fila VIP explicitamente e revise os grupos atribuídos no proxy/servidor.
- Restrinja plugin messaging e acesso do proxy às instâncias necessárias.
- Não disponibilize interfaces administrativas em servidores públicos de minigame.
- Evite usar `/reload` para atualizar a stack; faça reinicializações controladas e teste o encerramento dos plugins.

## 7. Dados de jogador e logs

Perfis incluem nomes, cargos, preferências, estatísticas e dados de jogo. Acesso direto ao MySQL e aos diretórios de configuração deve ser limitado. Não publique exports JSONL, dumps, backups, screenshots com dados reais ou logs completos em issues públicas.

O importador de migração deve ser executado por uma pessoa autorizada, em host restrito, com backups e sem registrar as variáveis de ambiente.

## 8. Limites conhecidos

A base foi construída para Minecraft 1.8.8 e APIs antigas. ProtocolLib, Bukkit e NMS têm superfícies que precisam ser avaliadas de acordo com a versão concreta do servidor. A existência de TLS no MySQL não protege o tráfego de jogo nem substitui firewall, controle de acesso ao proxy, patching do sistema operacional ou proteção da infraestrutura.

Este guia descreve controles identificados no repositório. Não afirma que toda consulta, dependência, integração ou configuração de produção foi auditada.
