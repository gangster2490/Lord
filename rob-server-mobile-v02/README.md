# ROB Server Mobile — Android starter

Native Kotlin + Jetpack Compose Android application. This is a **UI starter**, not a finished APK and not yet connected to ROB Server.

## Build
1. Install Android Studio (JDK 17 and Android SDK 35).
2. Open the `android-app` directory as an Android Studio project.
3. Allow Android Studio to sync Gradle; choose Build > Build APK(s).
4. The debug APK should appear under `app/build/outputs/apk/debug/` after a successful build.

No Gradle wrapper is bundled; Android Studio can provision Gradle. Alternatively install Gradle 8.9 and run `gradle :app:assembleDebug`.

## Scope
- Dark high-contrast dashboard, tabs, RU/DE switch, TikTok account selection (local UI only).
- Offline/not connected states are explicit; **no invented live metrics**.
- No internet permission, hardcoded secrets, network requests, terminal execution, or server mutations.

## Next integration
Read `../CLAUDE_TASK.md` and audit server APIs first. Add a protected HTTPS gateway, authentication, validated response models, lifecycle-aware networking and tests. Account selection must be enforced server-side; UI radio selection alone is not account switching.
