# ROB Server Mobile

Android-обёртка (Kotlin + Compose + WebView) для веб-интерфейсов ROB Server: Control Center, TikTok Studio, Terminal (ttyd), AdGen.
Адрес и порты вводятся в Настройках, в коде нет паролей и ключей. Вход выполняется в самих веб-страницах.

Сборка: `./gradlew :app:assembleDebug` (JDK 17+, Android SDK 35, `local.properties` с `sdk.dir`).
Release: задайте `ROB_KEYSTORE`, `ROB_KEYSTORE_PASSWORD`, `ROB_KEY_ALIAS`, `ROB_KEY_PASSWORD` и выполните `./gradlew :app:assembleRelease`. Ключ остаётся у вас.

Замечание: разрешён http (cleartext), чтобы работать по Tailscale/LAN. Для доступа из интернета используйте https.
