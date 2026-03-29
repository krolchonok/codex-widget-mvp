## Codex Widget MVP

Минимальный Android MVP для домашнего виджета Codex usage.

Что умеет:

- вставка полного `auth.json`
- refresh `access_token` по `refresh_token`, если токен протух или слишком старый
- запрос лимитов через `https://chatgpt.com/backend-api/wham/usage`
- вывод состояния в app screen и home-screen widget
- периодическое автообновление widget через `WorkManager`
- ручное обновление по тапу на widget

Как запустить:

1. Откройте каталог [android-widget-mvp](/home/krol/ushagent/android-widget-mvp) в Android Studio.
2. Дождитесь Gradle sync.
3. Запустите приложение на устройстве.
4. Вставьте полный `auth.json` в поле ввода.
5. Нажмите `Save`.
6. Нажмите `Refresh now`.
7. Добавьте widget `Codex Widget MVP` на домашний экран.

Быстрый сценарий:

- скопируйте `auth.json`
- откройте приложение
- нажмите `Paste and Refresh`

Нюансы:

- проект не собирался здесь, потому что в текущем окружении нет Android SDK/Gradle toolchain
- `auth.json` хранится локально в `EncryptedSharedPreferences` через `Android Keystore`
- это MVP, без background workers и polished UI
