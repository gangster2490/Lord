# ROB Neon — icon pack + wallpapers for Samsung One UI

Kotlin + Jetpack Compose app (RU/DE by system language) that ships:

- 30 icons (512 × 512 RGBA PNG, dark titanium / blue glass / red accents) in the standard ADW/Nova icon-pack structure,
- two wallpapers 1440 × 3120 (home: calm areas; lock: expressive),
- preview, searchable catalog with on-device diagnostics, wallpaper install via system dialogs, step-by-step Theme Park guide.

It is **not** a launcher and does not replace One UI. It requests **no permissions**, has no network, ads, analytics or accounts.

> **Status:** the APK builds, passes its tests and is correctly structured. Whether Samsung Theme Park lists and applies
> this pack on a Galaxy S24 Ultra is **NOT yet verified** — see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) and
> [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md).

Deliverables: `dist/ROB-Neon-1.0.1-debug.apk` (**signed with the Android debug key**), `dist/icons/`, `dist/wallpapers/`,
`dist/screenshots/` (Robolectric JVM renders of the real app — not device screenshots), `docs/`.

Build: [docs/BUILD.md](docs/BUILD.md). Asset generator (procedural, no screenshot cut-outs): `tools/`.
