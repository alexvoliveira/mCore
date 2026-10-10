# Plano de migração de dados

## Estado atual

O código ainda oferece persistência MySQL sem pool, HikariCP e MongoDB. MongoDB continua em uso no conversor e em caminhos de títulos personalizados. Portanto esta branch **não remove MongoDB nem migra automaticamente os dados existentes**.

## Sequência segura antes de adotar MySQL/Hikari como destino exclusivo

1. Fazer backup consistente e testado de MySQL e MongoDB; registrar versões, tamanho e contagem das coleções/tabelas.
2. Documentar mapeamento de cada campo e comportamento, inclusive perfil, boosters, títulos personalizados e documentos aninhados.
3. Criar esquema e migração versionada com uma tabela de histórico. A migração deve detectar incompatibilidades antes de escrever e ser idempotente.
4. Importar em ambiente isolado com logs e checkpoints; validar contagens, chaves, campos críticos e amostras de perfis.
5. Executar comparação de dados de origem/destino e testes de leitura/escrita; medir divergências antes do corte.
6. Definir janela de manutenção, backup de retorno e procedimento testado para voltar à origem caso as verificações falhem.
7. Só após aceite dos resultados, mudar a configuração de produção; preservar os dumps conforme a política de retenção.

Não execute scripts de criação simplificados sobre bancos já existentes sem comparar os schemas reais produzidos por `DataTable` e `DataTableInfo`. Esta etapa não inclui um runner de migração nem um importador MongoDB→MySQL validado.
