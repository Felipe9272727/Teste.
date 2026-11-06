# FLAPPY GOOSE (Android)

Jogo Flappy Bird cujo motor de jogo (fisica, colisao, RNG, pontos, obstaculos)
foi escrito *originalmente em Goose* (`goose/flappy.goose`), nao em JavaScript.

A tela e os toques sao entregues pela interface nativa Android (Java Canvas).
A ponte JNI C apenas liga essa interface as funcoes `export fn` de Goose.

## Como obter o APK no GitHub Actions
1. Crie um repositorio GitHub com o CONTEUDO desta pasta (na raiz).
2. Abra a aba **Actions** no repositorio.
3. Execute **Compilar Flappy Goose APK (Goose de verdade)** via **Run workflow**
   ou aguarde a execucao automatica no branch main.
4. Se o workflow terminar verde, abra a execucao, baixe o artifact **FlappyGoose-APK**
   e extraia o arquivo `app-debug.apk`.
5. Instale no Android. Habilite a permissao de instalar APK pelo app de arquivos,
   se o sistema solicitar. Nao e necessario liberar acessibilidade.

## De onde vem o codigo Android
- `goose/flappy.goose`: **logica da gameplay** (Goose)
- `android/app/src/main/cpp/bridge.c`: JNI, sem logica de jogo
- `android/app/src/main/java/.../FlappyGameView.java`: Canvas e touchscreen
- `.github/workflows/build-apk.yml`: compilacao online de Goose + NDK + Gradle

## Pipeline tecnico
`flappy.goose` -> `goose --standalone --header ... -o ...` ->
`flappy.c` + `flappy.h` -> clang do Android NDK -> `libflappygoose.so`
-> app Android Java + Gradle -> `app-debug.apk`.

## Plataformas
Configura para Android ARM64 (arm64-v8a), Android 7.0+.
O APK e um *debug build*, adequado para instalar e testar em dispositivo proprio.

## Notas importantes
A integracao Goose/Android ainda e experimental. Goose v2026.3 nao oferece
build oficial de APK. Este pacote e um PROJETO-FONTE, nao um APK pronto.
Ele NAO foi compilado nesta sessao por falta de compilador Goose e Android SDK/NDK
na maquina local. O workflow pode precisar de adaptacoes, especialmente no
runtime gerado pelo Goose para libc do Android. Confira o log de Actions.

Fonte oficial Goose: https://github.com/aardappel/goose
Manual export fn: https://aardappel.github.io/goose/docs/tutorial.html