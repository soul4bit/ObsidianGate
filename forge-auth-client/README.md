# ObsidianGate Forge Auth Client

Клиентский Forge 1.12.2 мод, который передает launcher-auth ticket из игры на сервер.

## Что делает мод

- Читает `.obsidiangate/session.json` из пути `-Dobsidiangate.sessionFile=...`.
- Проверяет, что ticket есть и не истек.
- Ждет завершения сетевого handshake.
- Отправляет одноразовый ticket серверу по каналу `ogauth`.

## Сборка

Сначала установи общий модуль:

```bash
mvn -f game-auth-common/pom.xml install
```

Потом собери клиентский мод:

```bash
mvn -f forge-auth-client/pom.xml clean package
```

Готовый jar:

```text
forge-auth-client/target/obsidiangate-forge-auth-client-0.1.0-SNAPSHOT.jar
```

## Установка

Обычно jar попадает в `dist/client/mods/` через скрипты релиза и скачивается лаунчером в:

```text
<game directory>/mods/obsidiangate-forge-auth-client-0.1.0-SNAPSHOT.jar
```

## Важно

- Без `-Dobsidiangate.sessionFile=...` мод ничего полезного не отправит.
- `session.json` создает лаунчер перед запуском игры.
- Ticket одноразовый: повторное подключение из уже запущенного клиента может получить отказ `used`.
