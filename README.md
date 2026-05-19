# ObsidianGate

`ObsidianGate` — это набор компонентов для Minecraft 1.12.2:

- JavaFX-лаунчер для синхронизации и запуска клиента
- Spring Boot API для регистрации, логина и выдачи игровых ticket
- клиентский Forge-мод для передачи ticket из игры
- серверный Forge-мод для проверки ticket на сервере
- общий Java 8-модуль с форматом `session.json` и HTTP-клиентом верификации

## Что умеет лаунчер

- показывает, к какому Minecraft-серверу пойдёт подключение
- загружает `manifest.json` по HTTP(S)
- синхронизирует файлы клиента по `manifest.json`
- при необходимости скачивает portable Java runtime
- при необходимости добирает официальный bootstrap Minecraft/Forge
- создаёт одноразовый игровой ticket для авторизованного пользователя
- записывает `.obsidiangate/session.json` в папку игры
- передаёт путь к `session.json` в игровой процесс через `-Dobsidiangate.sessionFile=...`
- запускает клиент через `launchTemplate`
- сохраняет настройки в `~/.obsidian-gate-launcher/launcher.properties`

## Что есть в интерфейсе

Сейчас в UI есть:

- главный экран со статусом сервера, manifest и кнопками `Предпросмотр` / `Играть`
- экран аккаунта
- экран `Моды` со списком `manifest.files[]` и runtime packages
- экран `Настройки` для локального конфига лаунчера
- журнал выполнения

Базовые настройки теперь можно менять прямо из экрана `Настройки` в UI. Файл `launcher.properties` по-прежнему остаётся источником локального конфига и может редактироваться вручную при необходимости.

## Требования

- Java 17 для лаунчера
- Maven 3.9+
- HTTP(S)-доступ к `manifest.json` и файлам модпака
- запущенный `auth-api`, если нужен вход через launcher auth

## Быстрый старт

Запуск лаунчера из исходников:

```bash
mvn javafx:run
```

Сборка лаунчера:

```bash
mvn package
```

Запуск собранного лаунчера:

```bash
java -jar target/obsidian-gate-launcher-0.1.0-SNAPSHOT.jar
```

Сборка auth API:

```bash
mvn -f auth-api/pom.xml clean package
```

Сборка общих и Forge-модулей:

```bash
mvn -f game-auth-common/pom.xml install
mvn -f forge-auth-client/pom.xml clean package
mvn -f forge-auth-server/pom.xml clean package
```

Сборка лаунчера делает fat jar, отдельные зависимости рядом не нужны.

## Автоматизация auth-релиза

Подготовка релиза auth-модулей:

```powershell
.\scripts\release-auth.ps1 -ManifestVersion 2026.05.08
```

Скрипт:

- собирает `game-auth-common`, `forge-auth-client`, `forge-auth-server`
- пересчитывает `sha256` и `size`
- обновляет запись клиентского auth-мода в `examples/manifest.json`
- складывает готовые артефакты и итоговый `manifest.json` в `dist/`
- пишет метаданные релиза в `dist/auth-release.json`

Деплой на сервер:

```powershell
.\scripts\deploy-auth.ps1 -Target minecraft@192.168.1.103
```

По умолчанию `deploy-auth.ps1`:

- загружает серверный и клиентский auth jar в домашнюю директорию `minecraft`
- устанавливает серверный jar в `~/mc-rpg/mods/`
- устанавливает клиентский jar в `/var/www/mc-rpg/client/mods/`
- обновляет `/var/www/mc-rpg/manifest.json`
- перезапускает `mc-rpg.service`

## Настройки launcher

Файл настроек:

```text
~/.obsidian-gate-launcher/launcher.properties
```

Минимальный пример:

```properties
username=Player
java.command=java
game.directory=C:\\Users\\<user>\\rpg-client
working.directory=
server.host=obsidiangates.duckdns.org
server.port=25565
manifest.url=http://obsidiangates.duckdns.org:8080/manifest.json
launch.template={java} -jar forge-1.12.2-14.23.5.2847.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}
update.files.before.launch=true
```

Значения по умолчанию:

- `server.host`: `obsidiangates.duckdns.org`
- `server.port`: `25565`
- `manifest.url`: `http://obsidiangates.duckdns.org:8080/manifest.json`
- `game.directory`: `~/rpg-client`

## Как работает launcher

1. Читает локальный `launcher.properties`.
2. Загружает `manifest.json`.
3. Если в manifest заполнен `launcher`, он может переопределить `serverHost`, `serverPort`, `workingDirectory` и `launchTemplate`.
4. Если в manifest заполнен `baseUrl`, файлы скачиваются относительно него. Если нет, базой считается директория самого `manifest.json`.
5. Перед скачиванием launcher сверяет файлы по `SHA-256` и, если указан, по `size`.
6. Если в manifest есть `runtime.packages`, launcher может скачать portable Java.
7. Если в manifest есть секция `minecraft`, launcher может добрать официальный Minecraft/Forge bootstrap.
8. Если пользователь авторизован, launcher создаёт game ticket через `POST /game/tickets`, пишет `.obsidiangate/session.json` и передаёт путь к нему в клиент.
9. После синхронизации строится launch-команда и запускается клиент.

## Как работает launcher auth

1. Пользователь логинится в лаунчере через `auth-api`.
2. Перед запуском игры лаунчер создаёт одноразовый ticket.
3. Лаунчер пишет `.obsidiangate/session.json` в папку игры.
4. Клиентский Forge-мод читает `session.json` и после сетевого handshake отправляет ticket на сервер по каналу `ogauth`.
5. Серверный Forge-мод вызывает `POST /game/tickets/verify`.
6. Если ticket валиден, игрок остаётся в игре. Если нет, сервер разрывает соединение.

## Плейсхолдеры `launchTemplate`

Поддерживаются:

- `{java}`
- `{username}`
- `{gameDir}`
- `{workingDir}`
- `{serverHost}`
- `{serverPort}`
- `{uuid}`
- `{accessToken}`
- `{userType}`
- `{gameSessionFile}`

Пример:

```text
{java} -jar forge-1.12.2-14.23.5.2847.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}
```

Если в шаблоне нет `{gameSessionFile}`, launcher сам добавит `-Dobsidiangate.sessionFile=...` сразу после java-команды.

## Формат manifest

Актуальный пример лежит в [examples/manifest.json](examples/manifest.json).

Коротко по ключевым полям:

- `schemaVersion`: версия схемы, сейчас используется `1`
- `baseUrl`: базовый URL для файлов модпака
- `launcher.serverHost`: хост Minecraft-сервера
- `launcher.serverPort`: порт Minecraft-сервера
- `launcher.authBaseUrl`: базовый URL `auth-api`
- `launcher.serverId`: идентификатор сервера для game ticket
- `launcher.workingDirectory`: рабочая папка внутри `game directory`
- `launcher.launchTemplate`: шаблон запуска клиента
- `launcherUpdate`: версия и URL нового launcher `.jar` для самообновления
- `runtime.packages[]`: portable runtime-пакеты
- `minecraft`: настройки официального bootstrap Minecraft/Forge
- `files[]`: список файлов для синхронизации

Минимальный пример:

```json
{
  "schemaVersion": 1,
  "id": "rpg",
  "version": "2026.05.05",
  "baseUrl": "http://obsidiangates.duckdns.org:8080/client/",
  "launcher": {
    "serverHost": "obsidiangates.duckdns.org",
    "serverPort": 25565,
    "workingDirectory": ".",
    "launchTemplate": ""
  },
  "launcherUpdate": {
    "version": "2026.05.13.2",
    "url": "launcher/obsidian-gate-launcher.jar",
    "sha256": "PUT_REAL_LAUNCHER_SHA256_HERE",
    "size": 12345678,
    "required": false
  },
  "files": [
    {
      "path": "mods/examplemod.jar",
      "sha256": "PUT_REAL_SHA256_HERE",
      "size": 54321
    }
  ]
}
```

## Как раздавать файлы

Обычная схема:

```text
/var/www/rpg/
  manifest.json
  launcher/
    obsidian-gate-launcher.jar
  client/
    runtime/
    mods/
    config/
```

Тогда:

- `manifest.url` можно сделать `http://obsidiangates.duckdns.org:8080/manifest.json`
- `baseUrl` можно сделать `http://obsidiangates.duckdns.org:8080/client/`
- `launcherUpdate.url` можно сделать `launcher/obsidian-gate-launcher.jar`; он считается относительно `manifest.json`

Важно: launcher ждёт именно HTTP(S)-раздачу `manifest.json` и файлов модпака. Это не Minecraft-порт `25565`.

## Ограничения

- launcher не удаляет лишние локальные файлы, которых больше нет в manifest
- `workingDirectory` должен оставаться внутри `game directory`
- `runtime.extractDir`, `runtime.javaPath` и `files[].path` тоже не должны выходить за пределы `game directory`
- game ticket одноразовый: повторный вход из уже запущенного клиента может получить `used`

## Ключевые файлы проекта

- `src/main/java/ru/mcrpg/launcher/LauncherShellApplication.java` — запуск JavaFX-окна
- `src/main/java/ru/mcrpg/launcher/LauncherShellController.java` — основной UI и действия синхронизации/запуска
- `src/main/java/ru/mcrpg/launcher/ModpackManifestClient.java` — загрузка `manifest.json`
- `src/main/java/ru/mcrpg/launcher/ModpackSyncService.java` — синхронизация файлов
- `src/main/java/ru/mcrpg/launcher/LaunchCommandBuilder.java` — сборка команды запуска
- `src/main/java/ru/mcrpg/launcher/SessionFileWriter.java` — запись `.obsidiangate/session.json`
- `src/main/java/ru/mcrpg/launcher/MinecraftBootstrapService.java` — официальный bootstrap Minecraft/Forge
- `src/main/java/ru/mcrpg/launcher/RuntimeSyncService.java` — portable Java runtime
- `auth-api/` — backend для аккаунтов, JWT и game ticket
- `game-auth-common/` — общий модуль session/ticket логики
- `forge-auth-client/` — Forge-клиент для отправки ticket
- `forge-auth-server/` — Forge-сервер для проверки ticket

## Full modpack deploy

Используйте `modpack/client/` как локальный источник файлов, которые должны публиковаться в `/var/www/mc-rpg/client/`.

Подготовка полного modpack-релиза:

```powershell
.\scripts\release-modpack.ps1 -ClientSourceDir modpack/client -ManifestVersion 2026.05.12
```

Скрипт:

- запускает `scripts/release-auth.ps1`
- копирует `modpack/client/` в `dist/client/`
- добавляет свежий `forge-auth-client` в `dist/client/mods/`
- пересчитывает `manifest.files[]` по фактическому содержимому `dist/client/`
- обновляет `runtime.packages[].sha256` и `runtime.packages[].size` для относительных runtime URL
- собирает launcher `.jar`, кладёт его в `dist/launcher/` и обновляет `launcherUpdate`
- пишет `dist/manifest.json`
- пишет `dist/modpack-release.json`

Деплой на сервер:

```powershell
.\scripts\deploy-modpack.ps1 -Target minecraft@192.168.1.103
```

Скрипт загружает:

- `dist/client/`
- `dist/server/`
- `dist/launcher/`
- `dist/manifest.json`
- серверный auth jar

и устанавливает их в:

- `~/mc-rpg/mods/`
- `~/mc-rpg/config/`
- `~/mc-rpg/scripts/`
- `/var/www/mc-rpg/client/`
- `/var/www/mc-rpg/launcher/`
- `/var/www/mc-rpg/manifest.json`

Полный цикл одной командой:

```powershell
.\scripts\publish-modpack.ps1 -Target minecraft@192.168.1.103
```

## Passwordless deploy setup

Один раз настройте SSH key и `sudo` wrapper:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-deploy-access.ps1 -Target minecraft@192.168.1.103
```

Re-run this command after changing `scripts/obsidiangate-remote-deploy.sh`, because the server keeps its own copy at `/usr/local/bin/obsidiangate-deploy`.

Скрипт:

- добавляет SSH alias `mc-rpg-deploy` в `~/.ssh/config`
- устанавливает ваш `~/.ssh/id_ed25519.pub` в `authorized_keys` пользователя `minecraft`
- ставит `/usr/local/bin/obsidiangate-deploy` на сервер
- добавляет `sudoers` правило для запуска этого wrapper без пароля

После этого основной сценарий:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish-modpack.ps1 -Target mc-rpg-deploy -ManifestVersion 2026.05.12
```

Если нужно вернуться к старому режиму с интерактивным `sudo`, используйте:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-modpack.ps1 -Target minecraft@192.168.1.103 -LegacyPromptSudo
```
