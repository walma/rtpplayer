[![](https://jitpack.io/v/walma/rtpplayer.svg)](https://jitpack.io/#walma/rtpplayer)

# RTP Player

Репозиторий разделен на два модуля:

- `:rtp-player` — core-библиотека с `LibVLC`-плеером
- `:rtp-player-ui` — переиспользуемый UI-слой с темой, `RtpPlayerScreen` и `RtpPlayerActivity`
- `:sample-app` — локальное demo-приложение для ручной проверки библиотек

На JitPack модуль `:sample-app` не подключается: `settings.gradle.kts` исключает его, если выставлена переменная окружения `JITPACK=true`. Это позволяет одновременно:

- разрабатывать библиотеку и демо в одном репозитории
- публиковать несколько артефактов из одного репозитория
- не ломать сборку JitPack из-за sample-модуля

## Локальная разработка

Для разработки и ручного тестирования используйте `:sample-app`:

```bash
./gradlew :sample-app:assembleDebug
```

`sample-app` зависит от UI-модуля:

```kotlin
implementation(project(":rtp-player-ui"))
```

`rtp-player-ui` при этом зависит от `rtp-player`, поэтому любые изменения в core и UI сразу доступны в demo-приложении без публикации тега и без обращения к JitPack.

## Подключение библиотек через JitPack

Добавьте репозиторий:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Дальше можно выбрать один из двух артефактов.

Только core:

```kotlin
dependencies {
    implementation("com.github.walma.rtpplayer:rtp-player:<tag>")
}
```

Готовый UI:

```kotlin
dependencies {
    implementation("com.github.walma.rtpplayer:rtp-player-ui:<tag>")
}
```

`rtp-player-ui` транзитивно подтянет `rtp-player`.

Если вы наследуетесь от `RtpPlayerActivity` в своем приложении и хотите использовать PiP, добавьте
`android:supportsPictureInPicture="true"` именно в manifest-описание вашего activity-класса.
Android проверяет поддержку PiP у фактически запущенного `Activity`, а не у базового класса из библиотеки.

## Релизы

В репозитории добавлен workflow `.github/workflows/release.yml`:

- на `push` тега вида `v*` создается GitHub Release
- после этого workflow запрашивает POM'ы двух модулей на JitPack и ждет их публикации

Так вы прогреваете JitPack сразу после релиза, а не в момент первого подключения новой версии в клиентском проекте.

Пример релиза:

```bash
git tag v0.2.0
git push origin v0.2.0
```

После этого станут доступны координаты:

```kotlin
implementation("com.github.walma.rtpplayer:rtp-player:v0.2.0")
implementation("com.github.walma.rtpplayer:rtp-player-ui:v0.2.0")
```

## JitPack

Для JitPack добавлен `jitpack.yml`:

```yaml
jdk:
  - openjdk17

install:
  - ./gradlew :rtp-player:publishReleasePublicationToMavenLocal :rtp-player-ui:publishReleasePublicationToMavenLocal
```

Это важно для текущего стека (`AGP 8.5.2`, `Kotlin 2.0.21`), потому что JitPack по умолчанию стартует не с той Java, которая нужна проекту.
