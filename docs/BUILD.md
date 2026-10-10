# Build local

## Requisitos

- JDK compatível com o alvo Java 8; use uma toolchain/ambiente de compilação validado para o legado.
- Gradle 8.14.3 disponível para gerar o Wrapper.
- Artefatos compatíveis com Spigot/CraftBukkit 1.8.8 (incluindo NMS `v1_8_R3`) e a API BungeeCord 1.8 acessíveis por HTTPS ou instalados em `mavenLocal()`.
- ProtocolLib 4.5.0 e PlaceholderAPI 2.10.5 compatíveis com o servidor legado.

O código usa classes NMS da versão legada; gere o artefato compatível utilizando os BuildTools oficiais do Spigot para `1.8.8`. Confirme que a API BungeeCord publicada coincide com as assinaturas utilizadas pelo código antes de aceitar o build.

## Gerar Wrapper e compilar

Na raiz do projeto, com Gradle instalado:

```sh
sh scripts/bootstrap-gradle-wrapper.sh
./gradlew clean build
```

O Wrapper ainda não está incluído nesta etapa porque não havia instalação de Gradle disponível para gerá-lo. Após gerar `gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.jar`, revise-os e adicione-os ao controle de versão.

O JAR de saída configurado é `build/libs/mCore.jar`. O `pom.xml` fica preservado enquanto a compilação Gradle não for validada. Não remova o Maven apenas por o arquivo Gradle existir.

## Segurança e ambiente

Não adicione credenciais de banco ao repositório. Configure credenciais nos arquivos/configurações de runtime e restrinja os privilégios da conta SQL ao necessário. As URLs novas exigem TLS com validação de identidade/certificado; configure certificados e nomes DNS corretos no ambiente.
