# ObsidianGate Auth API

Отдельный Spring Boot backend для:

- регистрации
- логина
- выдачи JWT access token
- обновления refresh token
- чтения и обновления профиля
- выдачи одноразовых game ticket
- проверки game ticket для Minecraft-сервера

## Сборка

```bash
mvn -f auth-api/pom.xml clean package
```

Готовый jar:

```text
auth-api/target/obsidiangate-auth-api-0.1.0-SNAPSHOT.jar
```

## Конфигурация

Скопируй `.env.example` и заполни секреты:

```bash
cp auth-api/.env.example auth-api/.env
```

Важные переменные:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `ACCESS_TOKEN_TTL_SECONDS`
- `REFRESH_TOKEN_TTL_DAYS`
- `GAME_TICKET_TTL_SECONDS`
- `SERVER_ID`

`SERVER_ID` должен совпадать с:

- `launcher.serverId` в `manifest.json`
- `-Dobsidiangate.serverId=...` у серверного Forge-мода

Схема PostgreSQL управляется Flyway-миграциями из `src/main/resources/db/migration`.
Hibernate работает в режиме `validate` и проверяет, что миграции уже применены.

## Локальный запуск

```bash
set DB_HOST=127.0.0.1
set DB_PORT=5432
set DB_NAME=obsidiangate
set DB_USER=obsidian
set DB_PASSWORD=CHANGE_ME
set JWT_SECRET=CHANGE_ME
set SERVER_PORT=8081
mvn -f auth-api/pom.xml spring-boot:run
```

## Деплой на сервер

1. Скопировать jar в `/home/minecraft/obsidiangate-auth/api/`.
2. Скопировать заполненный `.env` в `/home/minecraft/obsidiangate-auth/api/.env`.
3. Создать `systemd`-сервис по своей схеме деплоя.

## Основные endpoint

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /me`
- `PATCH /me`
- `POST /game/tickets`
- `POST /game/tickets/verify`
- `GET /health`
