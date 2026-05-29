# ObsidianGate Game Auth Common

Java 8 совместимый общий модуль для клиентской и серверной Forge-авторизации.

## Что внутри

- `LauncherSession` - модель `.obsidiangate/session.json`.
- `LauncherSessionFiles` - чтение и запись session-файла.
- `GameTicketProof` - данные ticket, которые отправляются из клиента на сервер.
- `TicketVerificationClient` - HTTP-клиент для `POST /game/tickets/verify`.
- `TicketVerificationResult` - результат проверки ticket.
- `SimpleJsonObject` - небольшой JSON-помощник без runtime-зависимости на Jackson.

## Сборка

```bash
mvn -f game-auth-common/pom.xml test
```

Установка в локальный Maven-репозиторий для Forge-модулей:

```bash
mvn -f game-auth-common/pom.xml install
```

## Где используется

- Лаунчер пишет `session.json` в совместимом формате.
- Клиентский Forge-мод читает `-Dobsidiangate.sessionFile=...`.
- Серверный Forge-мод проверяет ticket через `TicketVerificationClient`.

## Важно

- Модуль должен оставаться совместимым с Java 8.
- Не добавляй тяжелые runtime-зависимости без необходимости: этот jar встраивается в Forge-моды.
