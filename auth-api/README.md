# ObsidianGate Auth API

Spring Boot API для аккаунтов лаунчера, JWT-сессий и одноразовых игровых ticket.

## Возможности

- Регистрация и вход игрока.
- Выдача и обновление JWT access/refresh token.
- Чтение и обновление профиля.
- Каталог аватаров.
- Выдача игрового ticket перед запуском игры.
- Проверка игрового ticket серверным Forge-модом.
- Flyway-миграции PostgreSQL.

## Сборка

```bash
mvn -f auth-api/pom.xml clean package
```

Готовый jar:

```text
auth-api/target/obsidiangate-auth-api-0.1.0.jar
```

## Конфигурация

Скопируй пример окружения и заполни секреты локально:

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
- `AUTH_RATE_LIMIT_ENABLED`
- `AUTH_RATE_LIMIT_WINDOW_SECONDS`
- `AUTH_LOGIN_RATE_LIMIT`
- `AUTH_REGISTER_RATE_LIMIT`
- `AUTH_REFRESH_RATE_LIMIT`

`SERVER_ID` должен совпадать с `launcher.serverId` в `manifest.json` и с `-Dobsidiangate.serverId=...` у серверного Forge-мода.

## Локальный запуск

Командная строка Windows:

```bat
set DB_HOST=127.0.0.1
set DB_PORT=5432
set DB_NAME=obsidiangate
set DB_USER=obsidian
set DB_PASSWORD=CHANGE_ME
set JWT_SECRET=CHANGE_ME
set SERVER_PORT=8081
mvn -f auth-api/pom.xml spring-boot:run
```

## Маршруты API

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /me`
- `PATCH /me`
- `GET /avatars`
- `POST /game/tickets`
- `POST /game/tickets/verify`
- `GET /health`

## Деплой

Автоматический путь из корня проекта:

```powershell
.\scripts\release-auth.ps1 -ManifestVersion 2026.05.29
.\scripts\deploy-auth.ps1 -Target mc-rpg-deploy
```

Ручной путь:

1. Собрать jar.
2. Скопировать jar в `/home/minecraft/obsidiangate-auth/api/`.
3. Скопировать заполненный `.env` в `/home/minecraft/obsidiangate-auth/api/.env`.
4. Запустить через свой `systemd`-сервис или другой менеджер процессов.

## Важно

- Не коммить `.env`, пароли и `JWT_SECRET`.
- PostgreSQL схема управляется Flyway-миграциями из `src/main/resources/db/migration`.
- Hibernate работает в режиме `validate`, поэтому миграции должны быть применены до запуска.
