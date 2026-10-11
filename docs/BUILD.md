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

The default connection uses `sslMode=VERIFY_IDENTITY`: traffic is encrypted and the server certificate and hostname are validated. Do not change this to `DISABLED` or accept arbitrary certificates to work around a TLS error.

For Aiven, download the service CA certificate from the Aiven console and import it into a local PKCS12 truststore. Run this on the machine hosting the BungeeCord/Bukkit process (the command prompts for a truststore password):

```sh
keytool -importcert -alias aiven-mysql-ca -file ca.pem -keystore aiven-truststore.p12 -storetype PKCS12
```

Set these environment variables for the actual server process:
- `MCORE_MYSQL_PASSWORD`: the current MySQL password.
- `MCORE_MYSQL_TRUSTSTORE`: absolute path to `aiven-truststore.p12`.
- `MCORE_MYSQL_TRUSTSTORE_PASSWORD`: the password chosen for that truststore.

The application reads the truststore settings only when both its path and password are configured. Keep the truststore and credentials outside the repository, restrict file permissions, and restart the Java process after changing environment variables. The truststore contains the CA certificate, not the database password.

The checked-in `src/main/resources/config.yml` contains the Aiven host, port, database, and username, but it is only copied on first startup. On an existing installation, update `plugins/mCore/config.yml` manually to the same endpoint; do not put the database password there. Rotate any password that has been exposed and set the replacement through `MCORE_MYSQL_PASSWORD`.

Connection attempts now have explicit 10-second connect and 30-second socket timeouts. These timeouts do not replace correct DNS, network access, CA trust, or hostname validation.
