# Подготовка Модпака

`modpack/` - локальный источник файлов, из которых собирается клиентская и серверная публикация модпака.

## Структура

```text
modpack/
  client/
    config/
    mods/
    resourcepacks/
    runtime/
    scripts/
  server/
    config/
    mods/
    scripts/
    systemd/
    server-mods.txt
```

`client/` попадает в web root для скачивания лаунчером. `server/` содержит файлы, которые нужны выделенному серверу.
`server/server-mods.txt` перечисляет клиентские `mods/...`, которые копируются в серверный релиз; в `server/mods/`
остаются только server-only добавки.

## Как собирается релиз

1. `scripts/release-modpack.ps1` копирует `modpack/client/` в `dist/client/`.
2. Скрипт добавляет свежий `forge-auth-client` в `dist/client/mods/`.
3. Скрипт пересчитывает `manifest.files[]` по фактическим файлам из `dist/client/`.
4. Скрипт собирает launcher jar и кладет его в `dist/launcher/`.
5. Скрипт подготавливает серверные файлы в `dist/server/`.
6. `scripts/release-modpack.ps1` подписывает точные байты `dist/manifest.json` ключом Ed25519.
7. `scripts/deploy-modpack.ps1` выкладывает `dist/client/`, `dist/server/`, `dist/launcher/`, `dist/manifest.json` и `dist/manifest.json.sig` на сервер.

## Быстрый сценарий

Из корня проекта:

```powershell
.\scripts\publish-modpack.ps1 -Target mc-rpg-deploy -ManifestVersion 2026.05.29
```

## Ожидания от Manifest

- `manifest.baseUrl` должен указывать на опубликованный каталог `client/`.
- `manifest.runtime.packages[].url` должен совпадать с относительным путем архива переносимой Java внутри `client/`.
- `manifest.files[]` генерируется скриптом релиза, руками его обычно не правят.

## Важно

- Auth API jar сюда не кладется, он деплоится отдельным серверным процессом.
- Не клади в `modpack/` миры, приватные резервные копии, секреты и локальные отчеты о падениях.
- Перед публичным коммитом проверь, что в конфигах нет реального домена, IP, токенов или паролей.
