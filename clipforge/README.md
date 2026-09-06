# ClipForge

Нативное Android-приложение (Kotlin + Jetpack Compose) для генерации **сильных рекламных роликов товара** под **TikTok Shop**, **Instagram Reels** и **YouTube Shorts**.

ClipForge **не рендерит видеофайл**. Он собирает продакшн-пакет: хуки, подпись, хештеги, раскадровку, voiceover и готовый промпт **Veo 3.1**, который копируется в Gemini / Veo.

## Version

| Field | Value |
|---|---|
| applicationId | `de.spardirekt.clipforge` |
| versionName | `1.1.0` |
| minSdk | 26 |
| targetSdk | 35 |

Это отдельное приложение, не замена TikTok Shop Creator и Veo Prompt Pro. Фокус — конверсия на трёх коротких площадках, а не только немецкий TikTok Shop или один 8-секундный промпт.

## Что умеет

- Загрузка до 8 фото: товар, детали, использование, скрин описания
- Площадка: TikTok Shop / Reels / Shorts — разные CTA, лимиты подписи и хештегов
- Хронометраж 8 или 15 секунд
- Формулы: хук→демо→CTA, UGC, проблема→решение, распаковка, до/после, ASMR
- Язык ролика: RU / DE / EN (Veo-промпт всегда на английском)
- Архив готовых пакетов на устройстве
- Демо без сети: ключ `sk-demo`
- Ключ OpenAI в EncryptedSharedPreferences
- Подтверждение опасных действий, Share, отмена генерации

Правила пакета:

- товар с фото **заблокирован** (нельзя перерисовать)
- не меньше 80% хронометража — люди используют продукт
- без цен, скидок и фальшивой срочности
- TikTok Shop: CTA «в корзине», никогда «link in bio»

## Сборка

```bash
cd clipforge
echo "sdk.dir=$ANDROID_HOME" > local.properties   # если ещё нет
./gradlew assembleDebug testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

```bash
./gradlew installDebug
```

## Как пользоваться

1. **Настройки** → вставьте OpenAI ключ (или `sk-demo`).
2. **Студия** → фото товара, площадка, длительность, формула.
3. **Собрать ролик** → экран пакета.
4. Скопируйте Veo-пакет в Gemini / Veo, подпись — в TikTok / Reels / Shorts.

Модель по умолчанию: `gpt-4o` (vision). Ключ хранится локально в EncryptedSharedPreferences.

## Тесты

```bash
cd clipforge
./gradlew testDebugUnitTest
```

Покрыты: системный промпт, парсер JSON, лимиты площадок, CTA, замок Veo, раскадровка, демо-генератор, масштабирование фото.
