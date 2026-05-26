# ObsidianGate Forge Auth Server

## Серверные команды

- `/spawn` телепортирует игрока на точку спавна overworld.
- `/wptp <x> <y> <z> [yaw] [pitch]` телепортирует игрока к координатам метки. Команда доступна всем игрокам и используется меню телепорта Xaero Minimap/World Map.
- `/spawnprotect <info|on|off|radius|reload>` управляет защитой спавна вокруг `/setworldspawn`.
- `/kit start` выдаёт одноразовый стартовый набор.

Состав стартового набора:

- подписанный комплект кожаной брони
- каменный меч
- каменный топор
- каменная кирка
- каменная лопата
- 32 жареной говядины

Одноразовые выдачи китов хранятся в `obsidiangate/kit-claims.properties` в корне сервера. Файл лежит вне `config/`, поэтому деплой модпака не сбрасывает уже полученные наборы.

Серверный Forge 1.12.2-мод для проверки launcher-auth.

## Что делает мод

- принимает packet `ogauth` от клиентского мода
- держит список игроков, которые должны подтвердить launcher auth
- проверяет ticket через `POST /game/tickets/verify`
- кикает игроков, которые не прислали или не прошли проверку ticket

## Сборка

Сначала нужно установить общий модуль:

```bash
mvn -f game-auth-common/pom.xml install
```

Потом собрать серверный мод:

```bash
mvn -f forge-auth-server/pom.xml clean package
```

Готовый jar:

```text
forge-auth-server/target/obsidiangate-forge-auth-server-0.1.0-SNAPSHOT.jar
```

## Установка

Скопируй jar в папку модов Forge-сервера:

```text
<server>/mods/obsidiangate-forge-auth-server-0.1.0-SNAPSHOT.jar
```

## Runtime-параметры

- `-Dobsidiangate.authBaseUrl=http://127.0.0.1:8081`
- `-Dobsidiangate.serverId=obsidiangate-main`
- `-Dobsidiangate.authGraceSeconds=60`

Значения `obsidiangate.authGraceSeconds` ниже 60 секунд принудительно поднимаются до 60.

Переменные окружения, если свойства JVM не заданы:

- `OBSIDIANGATE_AUTH_BASE_URL`
- `OBSIDIANGATE_SERVER_ID`
- `OBSIDIANGATE_AUTH_GRACE_SECONDS`

## Важные замечания

- `obsidiangate.serverId` должен совпадать с `SERVER_ID` в `auth-api`
- мод рассчитан на dedicated Forge 1.12.2 server
- игроку нужен соответствующий клиентский мод, иначе сервер разорвёт соединение по таймауту auth
