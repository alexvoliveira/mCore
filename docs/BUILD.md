# Build e configuração do mCore

Este guia cobre o build Gradle da branch consolidada, a instalação de dependências legadas e a configuração da conexão MySQL.

## 1. Requisitos

- JDK 8.
- Gradle Wrapper versionado no repositório (distribuição configurada em `gradle/wrapper/gradle-wrapper.properties`).
- Spigot 1.8.8 gerado pelo BuildTools, incluindo o artefato compatível com NMS `v1_8_R3`.
- Acesso aos repositórios Maven definidos em `build.gradle.kts`.
- MySQL compatível com o Connector/J incluído no build para execução.

A versão 1.8.8 é antiga. Use uma toolchain isolada, valide os artefatos baixados e não misture classes NMS de versões diferentes.

## 2. Preparar Spigot 1.8.8

O código referencia diretamente classes Bukkit/NMS da versão 1.8.8. Gere os artefatos com o BuildTools oficial do Spigot para `1.8.8` e confirme que a dependência correspondente está disponível em `mavenLocal()`.

O workflow usa uma action de BuildTools para gerar os artefatos antes da compilação. Em uma máquina local, siga o procedimento oficial do Spigot BuildTools para instalar a API e os artefatos de servidor correspondentes.

## 3. Compilar com Gradle

Na raiz do projeto:

```bash
./gradlew --no-daemon clean build
```

No Windows:

```powershell
.\gradlew.bat --no-daemon clean build
```

O JAR final é:

```text
build/libs/mCore.jar
```

O plugin Shadow gera o artefato e inclui as dependências de runtime configuradas. O pacote Inventory API e annotations são relocados para namespaces internos do mCore, evitando colisões com outros plugins. As classes Bukkit/Spigot não são relocadas.

## 4. Dependências e escopos

O `build.gradle.kts` separa APIs fornecidas pelo servidor das bibliotecas que são empacotadas no plugin.

| Dependência | Escopo/uso |
| --- | --- |
| Spigot 1.8.8 | `compileOnly`; fornecido pelo servidor |
| BungeeCord API 1.8 | `compileOnly`; fornecida pelo proxy |
| ProtocolLib 4.5.0 | `compileOnly`; precisa estar no servidor Bukkit |
| PlaceholderAPI 2.10.5 | `compileOnly`; precisa estar no servidor Bukkit |
| Lombok | Processador de anotações |
| HikariCP 3.4.5 | Runtime |
| MySQL Connector/J 8.0.33 | Runtime |
| Inventory API | Runtime, com pacote relocacionado |
| JUnit/Gson | Dependências de teste |

A implementação Bukkit verifica a versão do PlaceholderAPI em runtime e espera 2.10.5. A instalação BungeeCord usa o descritor próprio e deve ser testada separadamente do ambiente Bukkit.

## 5. Maven legado

O arquivo `pom.xml` continua no repositório como referência durante a transição. Ele contém configurações históricas, incluindo caminho absoluto de BungeeCord específico de máquina e versões antigas de algumas dependências.

Não use esse POM como build canônico nem presuma que ele funcione em outra máquina sem revisão. A compilação documentada e configurada no workflow é feita com Gradle. Remover o POM deve ocorrer somente após a validação completa do build Gradle e do processo de release.

## 6. Configuração do banco MySQL

Na primeira inicialização, o plugin copia `src/main/resources/config.yml` para `plugins/mCore/config.yml`. Configure:

```yaml
database:
  mysql:
    host: "127.0.0.1"
    porta: "3306"
    nome: "mcore"
    usuario: "mcore"
    senha: ""
```

A senha pode ser fornecida pela variável de ambiente `MCORE_MYSQL_PASSWORD`. Ela tem precedência sobre `database.mysql.senha`. Prefira fornecer a senha no ambiente do processo Java, sem registrá-la em arquivos versionados, argumentos de processo ou logs.

Use um usuário MySQL exclusivo para o mCore, com privilégios apenas no schema que ele utiliza. A aplicação cria as tabelas e executa as migrações no startup, portanto a conta precisa ter as permissões DDL e DML necessárias durante a implantação. Em operação, reveja se esses privilégios podem ser reduzidos após a preparação do schema.

### TLS e certificado CA

A conexão JDBC usa `sslMode=VERIFY_IDENTITY`: o tráfego é criptografado, a cadeia do certificado é validada e o hostname precisa corresponder ao certificado. Não configure `sslMode=DISABLED` nem aceite certificados arbitrários.

Para uma instância Aiven:

1. Abra o serviço MySQL no console do provedor.
2. Baixe o certificado CA em *Overview → Connection information*.
3. Salve como `plugins/mCore/ca.pem` no host do processo Java, ou defina `MCORE_MYSQL_CA_CERT` com o caminho absoluto para o PEM.
4. Reinicie o processo Java depois de alterar as variáveis.

O código também mantém o caminho de truststore PKCS12: configure `MCORE_MYSQL_TRUSTSTORE` e `MCORE_MYSQL_TRUSTSTORE_PASSWORD`. Quando a CA PEM está disponível, ela tem precedência sobre o truststore configurado.

Se a CA estiver ausente ou o hostname não corresponder, corrija a configuração do certificado/DNS. Não desative a validação TLS como solução temporária.

## 7. Configurações de execução

Os principais arquivos são copiados para `plugins/mCore/`:

| Arquivo | Uso |
| --- | --- |
| `config.yml` | Host, porta, schema e usuário MySQL |
| `core.yml` | Spawn, proteção, permissão VIP e intervalos de limpeza |
| `roles.yml` | Definição de cargos, prefixos, permissões e visibilidade |
| `utils.yml` | Opções auxiliares, incluindo comportamentos de fake players |
| `deliveries.yml` | Recompensas/entregas |
| `titles.yml` | Títulos personalizados |

Os nomes e estruturas exatos podem evoluir conforme o código. Use os arquivos gerados pela mesma versão do JAR que será executada.

## 8. Integração contínua

O workflow de build executa checkout, configura Java 8, prepara Spigot 1.8.8, roda os testes Python do importador e compila o plugin com Gradle Wrapper. O artefato esperado é `build/libs/mCore.jar`.

Um workflow definido no repositório não significa que o build atual foi aprovado. Verifique o resultado da execução associada ao commit antes de promover o JAR para produção.

