# Build local

## Requisitos

- JDK compatível com o alvo Java 8; use uma toolchain/ambiente de compilação validado para o legado.
- Gradle 8.14.3 disponível para gerar o Wrapper.
- Artefatos compatíveis com Spigot/CraftBukkit 1.8.8 (incluindo NMS `v1_8_R3`) e a API BungeeCord 1.8 acessíveis por HTTPS ou instalados em `mavenLocal()`.
- ProtocolLib 4.5.0 e PlaceholderAPI 2.10.5 compatíveis com o servidor legado. O build consulta as URLs atuais de ProtocolLib e também repositórios espelho HTTPS para os artefatos antigos que não são mais encontrados em todos os repositórios originais.

O código usa classes NMS da versão legada; gere o artefato compatível utilizando os BuildTools oficiais do Spigot para `1.8.8`. Confirme que a API BungeeCord publicada coincide com as assinaturas utilizadas pelo código antes de aceitar o build.

## Gerar Wrapper e compilar

Na raiz do projeto, com Gradle instalado:

```sh
sh scripts/bootstrap-gradle-wrapper.sh
./gradlew clean build
```

O Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` e propriedades com checksum da distribuição) está versionado. Mantenha os scripts e o JAR alinhados com a versão definida em `gradle-wrapper.properties`.

O JAR de saída configurado é `build/libs/mCore.jar`. O `pom.xml` fica preservado enquanto a compilação Gradle não for validada. Não remova o Maven apenas por o arquivo Gradle existir.

## Segurança e ambiente

Não adicione credenciais de banco ao repositório. Configure credenciais nos arquivos/configurações de runtime e restrinja os privilégios da conta SQL ao necessário. As URLs novas exigem TLS com validação de identidade/certificado; configure certificados e nomes DNS corretos no ambiente.


## MySQL TLS with Aiven

The JDBC URL uses `sslMode=VERIFY_IDENTITY`: traffic is encrypted, the server certificate chain is validated, and the hostname must match the certificate. Do not set `sslMode=DISABLED` or accept arbitrary certificates.

1. In the Aiven Console, open the MySQL service and download **CA certificate** from **Overview → Connection information**.
2. Save the downloaded file as `plugins/mCore/ca.pem` on the machine running BungeeCord (or the Bukkit server if it connects directly to MySQL). The plugin can read this PEM certificate directly and generate a temporary PKCS12 truststore; running `keytool` is not required.
3. Alternatively, set `MCORE_MYSQL_CA_CERT` to the absolute path of the downloaded PEM file.
4. Set `MCORE_MYSQL_PASSWORD` in the environment of the actual Java process. Restart the process after changing environment variables.

The previous PKCS12 method remains supported: set both `MCORE_MYSQL_TRUSTSTORE` and `MCORE_MYSQL_TRUSTSTORE_PASSWORD`. When a CA PEM file is present, it takes precedence and is converted into a temporary truststore for the JDBC connection.

For the existing installation, the config file is usually `plugins/mCore/config.yml`; the checked-in resource only supplies defaults for new installations. Confirm it contains the current Aiven host, port, database, and username. Do not put the database password in the repository or in a version-controlled YAML file. Rotate passwords that have been exposed.

If the Aiven CA file is missing, the plugin now fails early with an explicit configuration message instead of proceeding to an opaque TLS handshake failure. TLS certificate and hostname verification remain enabled.
