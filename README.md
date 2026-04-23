[![](https://jitpack.io/v/walma/rtpplayer.svg)](https://jitpack.io/#walma/rtpplayer)

# RTP Player

Репозиторий разделен на два модуля:

- `:rtp-player` — Android-библиотека на `LibVLC`, публикуемая через JitPack
- `:sample-app` — локальное demo-приложение для ручной проверки библиотеки

На JitPack модуль `:sample-app` не подключается: `settings.gradle.kts` исключает его, если выставлена переменная окружения `JITPACK=true`. Это позволяет одновременно:

- разрабатывать библиотеку и демо в одном репозитории
- не менять публичную координату зависимости
- не ломать сборку JitPack из-за sample-модуля

## Локальная разработка

Для разработки и ручного тестирования используйте `:sample-app`:

```bash
./gradlew :sample-app:assembleDebug
```

`sample-app` зависит от библиотеки напрямую:

```kotlin
implementation(project(":rtp-player"))
```

Это значит, что любые изменения в библиотеке сразу доступны в demo-приложении без публикации тега и без обращения к JitPack.

## Подключение библиотеки через JitPack

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

И зависимость:

```kotlin
dependencies {
    implementation("com.github.walma:rtpplayer:<tag>")
}
```

## Релизы

В репозитории добавлен workflow `.github/workflows/release.yml`:

- на `push` тега вида `v*` создается GitHub Release
- после этого workflow запрашивает POM с JitPack и ждет публикации артефакта

Так вы прогреваете JitPack сразу после релиза, а не в момент первого подключения новой версии в клиентском проекте.

## JitPack

Для JitPack добавлен `jitpack.yml`:

```yaml
jdk:
  - openjdk17

install:
  - ./gradlew :rtp-player:publishReleasePublicationToMavenLocal
```

Это важно для текущего стека (`AGP 8.5.2`, `Kotlin 2.0.21`), потому что JitPack по умолчанию стартует не с той Java, которая нужна проекту.
