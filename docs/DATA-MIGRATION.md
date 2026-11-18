# Migração de dados: MongoDB para MySQL

A branch inclui um importador offline para transferir dados de exports JSONL do MongoDB legado para o schema MySQL utilizado pelo mCore. O importador fica em `tools/migrate_mongodb_export.py`, não é empacotado no plugin e não usa MongoDB Driver no runtime.

**Não remova o banco MongoDB nem os backups de origem até que a importação e a validação funcional tenham sido concluídas e aprovadas.**

## 1. O que a ferramenta importa

A ferramenta lê três coleções exportadas com `mongoexport`:

| Coleção de origem | Destino |
| --- | --- |
| `Profile` | `mCoreProfile`, `mCoreSkyWars`, `mCoreTheBridge`, `mCoreMurder` |
| `mCoreTitles` | `mCoreTitles` |
| `mCoreNetworkBooster` | `mCoreNetworkBooster` |

As tabelas de perfil são criadas pelo próprio mCore a partir das definições em `DataTable`. O importador exige as seis tabelas de destino, consulta `INFORMATION_SCHEMA` e valida os nomes das colunas antes de escrever.

Campos derivados de leaderboard (`totalkills`, `totalwins` e `totalpoints`) são ignorados no mapeamento porque o backend SQL calcula os rankings a partir das colunas de estatísticas. Campos desconhecidos ou sem mapeamento interrompem a execução em vez de serem descartados silenciosamente.

## 2. Preparar backups e janela de migração

Antes de começar:

1. Faça backup consistente do MongoDB de origem.
2. Faça backup do MySQL de destino, se ele já tiver dados.
3. Confirme que os arquivos exportados são legíveis e que há espaço suficiente para o banco e os arquivos temporários.
4. Planeje uma janela sem gravações concorrentes na origem durante o export.
5. Use uma instância de homologação primeiro.
6. Não versione exports, dumps reais de jogadores, URI com senha ou credenciais.

Não execute a importação enquanto servidores estiverem gravando no mesmo banco de destino.

## 3. Exportar as coleções

Use uma versão compatível do MongoDB Database Tools. O formato deve ser JSONL padrão, com um documento JSON por linha. Não use `--jsonArray`.

```bash
mongoexport --uri "$MCORE_MONGODB_URI" --db mCore --collection Profile --out ./Profile.jsonl
mongoexport --uri "$MCORE_MONGODB_URI" --db mCore --collection mCoreTitles --out ./mCoreTitles.jsonl
mongoexport --uri "$MCORE_MONGODB_URI" --db mCore --collection mCoreNetworkBooster --out ./mCoreNetworkBooster.jsonl
```

Configure `MCORE_MONGODB_URI` no ambiente, sem imprimir seu valor em logs. Se uma coleção estiver vazia, forneça um arquivo JSONL vazio para ela.

O script reconhece valores comuns do Extended JSON, como `$numberLong`, `$numberInt`, `$numberDouble`, `$numberDecimal`, `$oid` e `$date`.

## 4. Preparar o schema MySQL

Configure a conexão MySQL do mCore e o TLS. Inicie uma vez uma versão compatível do plugin para que ele crie ou atualize as tabelas:

- `mCoreProfile`;
- `mCoreSkyWars`;
- `mCoreTheBridge`;
- `mCoreMurder`;
- `mCoreNetworkBooster`;
- `mCoreTitles`.

Depois, desligue todos os processos mCore que apontam para o banco de destino antes de executar a importação. Isso evita que a aplicação altere os registros durante o processo.

O importador precisa de um usuário de banco com permissão de leitura do schema e de escrita nas tabelas de destino. Configure as variáveis no ambiente de migração:

```bash
export MCORE_MYSQL_HOST="db.example.internal"
export MCORE_MYSQL_PORT="3306"
export MCORE_MYSQL_DATABASE="mcore"
export MCORE_MYSQL_USER="mcore_migration"
export MCORE_MYSQL_PASSWORD="..."
# Opcional: caminho da CA do servidor.
export MCORE_MYSQL_SSL_CA="/caminho/seguro/ca.pem"
```

O importador é um processo Python independente. Ele usa `mysql-connector-python`; a variável `MCORE_MYSQL_SSL_CA` pertence a essa ferramenta e não é a mesma configuração de CA usada pelo plugin Java.

## 5. Instalar a dependência Python

```bash
python -m pip install mysql-connector-python
```

Prefira um ambiente virtual isolado para não misturar dependências da ferramenta de migração com o ambiente de build do plugin.

## 6. Validar sem escrever

Execute primeiro com `--dry-run`:

```bash
python tools/migrate_mongodb_export.py \
  --profiles ./Profile.jsonl \
  --titles ./mCoreTitles.jsonl \
  --boosters ./mCoreNetworkBooster.jsonl \
  --dry-run
```

O modo de validação conecta ao MySQL, inspeciona as seis tabelas, confere o mapeamento e reporta a quantidade de documentos e linhas planejadas. Ele não grava os registros. Corrija todos os erros antes de executar a importação.

## 7. Importar

A importação padrão usa `INSERT IGNORE`: linhas já existentes não são substituídas. Isso torna a execução repetível após falhas parciais, mas não é uma transação global. O script confirma lotes de até 200 linhas; se falhar no meio, lotes anteriores podem já ter sido gravados.

```bash
python tools/migrate_mongodb_export.py \
  --profiles ./Profile.jsonl \
  --titles ./mCoreTitles.jsonl \
  --boosters ./mCoreNetworkBooster.jsonl
```

A opção `--overwrite` atualiza colunas de registros existentes e deve ser usada somente após backup, revisão de amostras e aprovação explícita. Não execute essa opção como tentativa genérica de corrigir contagens.

## 8. Conferir o resultado

O relatório apresenta documentos de origem, linhas tentadas, linhas afetadas e contagens por tabela antes/depois. Esses números são necessários, mas não provam equivalência funcional.

Compare valores reais de uma amostra representativa:
- perfil geral, cash, cargo e preferências;
- estatísticas SkyWars, The Bridge e Murder;
- títulos personalizados;
- boosters ativos, expirados e ausentes;
- valores vazios/nulos e documentos com campos opcionais.

Depois, em homologação, teste login/entrada, carregamento e salvamento de perfil, preferências, leaderboards, entregas, títulos, boosters, filas e transferência entre servidores Bukkit/BungeeCord.

Mantenha backups de origem e destino até que todas as verificações sejam aceitas.

## 9. Limitações e recuperação

- O importador não cria o schema; uma versão compatível do mCore precisa inicializar as tabelas antes.
- O script importa exports JSONL, não conecta diretamente ao MongoDB.
- O modo padrão não sobrescreve linhas existentes.
- A importação é confirmada em lotes e pode ficar parcialmente concluída após falha.
- Campos não mapeados causam erro intencionalmente; não remova essa validação sem planejar como preservar os dados.
- O relatório de contagem não substitui validação de conteúdo e testes de uso.
- Não apague o MongoDB antigo até que o sistema esteja operando normalmente com o MySQL migrado e os backups tenham sido verificados.

