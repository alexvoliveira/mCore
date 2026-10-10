# Migração de dados MongoDB → MySQL/HikariCP

O código de produção do mCore agora usa somente MySQL por HikariCP. **Não remova a base MongoDB nem seus backups até concluir a importação e as verificações descritas aqui.** A ferramenta é offline e não é empacotada no plugin; ela lê exports JSONL gerados por `mongoexport`, sem incluir MongoDB driver no runtime.

## 1. Faça backups antes de alterar o ambiente

1. Faça um backup consistente do banco MySQL de destino, se ele já existir.
2. Faça um backup consistente de MongoDB e confirme que os arquivos exportados são legíveis.
3. Use uma janela de manutenção ou impeça gravações concorrentes no sistema de origem durante os exports e a importação.
4. Não versione arquivos de export, credenciais, URIs com senha ou dumps reais de jogadores.

## 2. Exporte as três coleções

Use uma versão compatível do MongoDB Database Tools e exporte no formato padrão JSONL (um documento JSON por linha; **não** use `--jsonArray`):

```sh
mongoexport --uri "$MCORE_MONGODB_URI" --db mCore --collection Profile --out ./Profile.jsonl
mongoexport --uri "$MCORE_MONGODB_URI" --db mCore --collection mCoreTitles --out ./mCoreTitles.jsonl
mongoexport --uri "$MCORE_MONGODB_URI" --db mCore --collection mCoreNetworkBooster --out ./mCoreNetworkBooster.jsonl
```

Configure `MCORE_MONGODB_URI` fora do repositório e não imprima seu valor em logs. O script espera esses três exports, inclusive arquivos vazios quando a coleção não tem documentos.

## 3. Prepare o destino

Configure o plugin com os dados MySQL corretos e TLS configurado para que o certificado corresponda ao hostname usado na conexão. Inicie uma vez uma versão compatível do mCore para que ela crie/atualize `mCoreProfile`, `mCoreSkyWars`, `mCoreTheBridge`, `mCoreMurder`, `mCoreNetworkBooster` e `mCoreTitles`; depois desligue o servidor antes de executar a importação.

O importador exige as seis tabelas e valida as colunas contra o schema real. Se encontrar colunas sem mapeamento, ele para com erro para evitar descartar silenciosamente dados.

## 4. Execute a ferramenta offline

Instale o cliente Python apenas no ambiente de migração:

```sh
python -m pip install mysql-connector-python
```

Configure as variáveis do destino. Não passe senhas como argumentos nem as registre no terminal:

```sh
export MCORE_MYSQL_HOST="db.example.internal"
export MCORE_MYSQL_PORT="3306"
export MCORE_MYSQL_DATABASE="server"
export MCORE_MYSQL_USER="mcore_migration"
export MCORE_MYSQL_PASSWORD="..."
# Opcional: caminho para CA personalizada que valida o certificado do servidor.
export MCORE_MYSQL_SSL_CA="/caminho/seguro/ca.pem"
```

Primeiro execute em modo de validação sem escrita:

```sh
python tools/migrate_mongodb_export.py --profiles ./Profile.jsonl --titles ./mCoreTitles.jsonl --boosters ./mCoreNetworkBooster.jsonl --dry-run
```

Revise os erros e o mapeamento. Depois faça a importação padrão:

```sh
python tools/migrate_mongodb_export.py --profiles ./Profile.jsonl --titles ./mCoreTitles.jsonl --boosters ./mCoreNetworkBooster.jsonl
```

O modo padrão usa `INSERT IGNORE`: não sobrescreve registros já existentes e pode ser executado novamente. Use `--overwrite` somente após backup e decisão explícita, pois ele atualiza as linhas existentes com os dados do export.

## 5. Verifique antes do corte

O relatório mostra documentos de origem, linhas tentadas, linhas afetadas e contagens por tabela antes/depois. Compare esses números com o MongoDB, valide manualmente uma amostra de perfis, preferências, títulos, boosters e estatísticas e teste login, carregamento/salvamento e leaderboards no servidor de homologação.

O relatório de contagens não prova sozinho equivalência funcional. Faça também uma comparação de valores e teste os fluxos reais de Bukkit/BungeeCord antes de direcionar tráfego de produção para o banco migrado.

Se a importação falhar após alguns lotes, o script pode já ter confirmado lotes anteriores. O modo padrão é repetível; corrija a causa e execute novamente. Preserve os backups de origem e destino até que as verificações sejam aceitas.

## O que a ferramenta mapeia

- Coleção `Profile`: campos de perfil para `mCoreProfile` e subdocumentos para `mCoreSkyWars`, `mCoreTheBridge` e `mCoreMurder`.
- Coleção `mCoreNetworkBooster`: `_id` para `id`, além de `booster`, `multiplier` e `expires`.
- Coleção `mCoreTitles`: `_id` para `id`, além de `name` e `description`.
- Valores derivados `totalkills`, `totalwins` e `totalpoints` são ignorados porque o backend SQL calcula leaderboards a partir das colunas de estatísticas.

Se os schemas de origem tiverem campos adicionais não mapeados, a ferramenta interrompe a importação. Não altere o script para ignorar esses campos sem identificar como preservá-los no modelo SQL.
