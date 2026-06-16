# ObsidianGate Forge Auth Server

Серверный Forge 1.12.2 мод для проверки launcher-auth ticket и набора серверных команд.

## Авторизация

Мод:

- Принимает пакет `ogauth` от клиентского мода.
- Держит список игроков, которые должны подтвердить авторизацию лаунчера.
- Проверяет ticket через `POST /game/tickets/verify`.
- Отключает игрока, если ticket не пришел, истек или не прошел проверку.

Параметры запуска:

```text
-Dobsidiangate.authBaseUrl=http://127.0.0.1:8081
-Dobsidiangate.serverId=obsidiangate-main
-Dobsidiangate.authGraceSeconds=60
```

Переменные окружения, если JVM-свойства не заданы:

- `OBSIDIANGATE_AUTH_BASE_URL`
- `OBSIDIANGATE_SERVER_ID`
- `OBSIDIANGATE_AUTH_GRACE_SECONDS`

`obsidiangate.authGraceSeconds` ниже 60 секунд принудительно поднимается до 60.

## Серверные команды

- `/spawn` - телепорт на точку spawn overworld.
- `/wptp <x> <y> <z> [yaw] [pitch]` - телепорт к координатам, используется меню Xaero Minimap/World Map.
- `/spawnprotect <info|on|off|radius|reload>` - управление защитой spawn вокруг `/setworldspawn`.
- `/kit start` - одноразовый стартовый набор.
- `/home`, `/sethome`, `/delhome` - дома игрока.
- `/back` - возврат к предыдущей позиции.
- `/call`, `/tpaccept`, `/tpdeny` - запросы телепорта.
- `/claim` - регионы игрока.

Состояние одноразовых kit хранится в `obsidiangate/kit-claims.properties` в корне сервера. Файл лежит вне `config/`, поэтому деплой модпака не сбрасывает уже полученные наборы.

## Сборка

Сначала установи общий модуль:

```bash
mvn -f game-auth-common/pom.xml install
```

Потом собери серверный мод:

```bash
mvn -f forge-auth-server/pom.xml clean package
```

Готовый jar:

```text
forge-auth-server/target/obsidiangate-forge-auth-server-0.1.0.jar
```

## Установка

Скопируй jar в папку модов выделенного Forge-сервера:

```text
<server>/mods/obsidiangate-forge-auth-server-0.1.0.jar
```

## Диагностика киков

Мод пишет короткие маркеры в лог и добавляет их в причину отключения:

- `AUTH_TIMEOUT_NO_TICKET` - клиент не прислал launcher ticket вовремя.
- `AUTH_TICKET_EXPIRED` - ticket истек до проверки.
- `AUTH_TICKET_SERVER_MISMATCH` - ticket выпущен для другого `serverId`.
- `AUTH_API_UNREACHABLE` - сервер не смог проверить ticket через Auth API.

## Важно

- `obsidiangate.serverId` должен совпадать с `SERVER_ID` в `auth-api`.
- Мод рассчитан на выделенный Forge 1.12.2 сервер.
- Игроку нужен соответствующий клиентский мод, иначе сервер разорвет соединение по таймауту авторизации.
