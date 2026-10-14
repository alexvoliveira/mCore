# Build local

## Requisitos

- JDK 21.
- Gradle Wrapper 8.14.3 (versionado no repositório).
- Spigot/CraftBukkit 1.21.1 gerado pelo BuildTools e instalado em `mavenLocal()` para compilar contra o artefato `org.spigotmc:spigot:1.21.1-R0.1-SNAPSHOT`.
- ProtocolLib 5.4.0 e PlaceholderAPI 2.12.3 para compilar. Em runtime, use versões compatíveis com Spigot 1.21.1 e Java 21; forks em desenvolvimento precisam ser validadas no servidor de teste.

O repositório `alexvoliveira/Spigot-1.21.1` contém a API Bukkit, mas não contém as implementações internas CraftBukkit/NMS. Por isso, a compilação não pode obter classes internas do servidor a partir desse repositório. A implementação ativa desta branch usa a API pública Bukkit e ProtocolLib; os fontes antigos `v1_8_R3` ficam fora do source set.

## Compilar

Na raiz do projeto:

```sh
./gradlew --no-daemon clean build
```

O artefato de saída é `build/libs/mCore.jar`. O workflow `mCore - JDK 21 / Spigot 1.21.1 validation` executa testes do importador e compila o JAR no GitHub Actions.

O `pom.xml` histórico ainda descreve dependências e caminhos locais da versão antiga. Use o Gradle Wrapper como build oficial desta branch; não publique artefatos gerados pelo Maven.

## Segurança e ambiente

Não adicione credenciais de banco ao repositório. Configure credenciais nos arquivos/configurações de runtime e restrinja os privilégios da conta SQL ao necessário. As conexões exigem validação de TLS e do hostname.


## MySQL TLS with Aiven

The JDBC URL uses `sslMode=VERIFY_IDENTITY`: traffic is encrypted, the server certificate chain is validated, and the hostname must match the certificate. Do not set `sslMode=DISABLED` or accept arbitrary certificates.

1. In the Aiven Console, open the MySQL service and download **CA certificate** from **Overview → Connection information**.
2. Save the downloaded file as `plugins/mCore/ca.pem` on the machine running BungeeCord (or the Bukkit server if it connects directly to MySQL). The plugin can read this PEM certificate directly and generate a temporary PKCS12 truststore; running `keytool` is not required.
3. Alternatively, set `MCORE_MYSQL_CA_CERT` to the absolute path of the downloaded PEM file.
4. Set `MCORE_MYSQL_PASSWORD` in the environment of the actual Java process. Restart the process after changing environment variables.

The previous PKCS12 method remains supported: set both `MCORE_MYSQL_TRUSTSTORE` and `MCORE_MYSQL_TRUSTSTORE_PASSWORD`. When a CA PEM file is present, it takes precedence and is converted into a temporary truststore for the JDBC connection.

For the existing installation, the config file is usually `plugins/mCore/config.yml`; the checked-in resource only supplies defaults for new installations. Confirm it contains the current Aiven host, port, database, and username. Do not put the database password in the repository or in a version-controlled YAML file. Rotate passwords that have been exposed.

If the Aiven CA file is missing, the plugin now fails early with an explicit configuration message instead of proceeding to an opaque TLS handshake failure. TLS certificate and hostname verification remain enabled.
