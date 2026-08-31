# Visual baseline

This folder is the **canonical look** of Veo Prompt Pro. Keep it.

The emulator screenshots here are the source of truth for layout, color, type, and chrome. New work (features, copy, bugfixes) must match this design. Do not introduce a dark full-screen theme, neon-on-black TikTok styling, or a Material default restyle.

## Locked look

| Token | Role |
|---|---|
| Light lavender page `#F7F5FB` with glow `#EAE4F8` | Screen background |
| Navy cards `#141B3A`, 26.dp corners, 22.dp padding | Photo / settings / history cards |
| Violet `#7C5CFF` → blue `#3D8BFF` gradient | Primary button, selected chips, logo tile |
| Gradient title on the Create heading | “Генератор промптов для видео” |
| Dark navy bottom bar `#0F1738` + lilac selected pill `#E6DEFF` | Create / History / Settings |
| White-on-gradient primary CTA, 60.dp tall, 20.dp corners | “✦ Создать VEO Prompt” |

Tokens live in `app/src/main/java/de/spardirekt/agents/pro/ui/theme/Theme.kt`. Unit tests in `VisualBaselineTokensTest` fail if those colors or radii change.

## Reference frames

| File | Screen |
|---|---|
| `01_create.png` | Create — header, photo card, wish, voice, mode |
| `02_create_generate.png` | Create — generate button sitting above the bottom nav |
| `03_settings.png` | Settings — API key + defaults |
| `04_history.png` | History — empty state |

Captured on Android 15 emulator, 1080×2400, from the 3.2.17 UI.
