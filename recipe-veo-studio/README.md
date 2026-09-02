# Recipe VEO Studio

Личная студия промптов **Veo 3.1**. Загружаете фото товара — приложение разбирает кадры и собирает production-промпт на ровно 8.0s (12 секций: FORMAT … HASHTAGS), voiceover, title и 5 hashtags.

Ролик снимает Veo, не это приложение. Первый запуск уже готов к генерации (демо-режим `sk-demo`). Свой OpenAI-ключ можно подставить в настройках.

| | |
|---|---|
| applicationId | `de.spardirekt.recipeveo` |
| minSdk / targetSdk | 26 / 35 |
| version | 2.0.0 |

## Экраны

1. **Создать** — фото товара (до 15), пожелание, голос, режим, TikTok Shop
2. **История** — черновики и готовые пакеты
3. **Результат** — копирование VEO-промпта, voiceover, title, hashtags
4. **Настройки** — ключ OpenAI / `sk-demo`
