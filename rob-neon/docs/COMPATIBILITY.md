# Theme Park compatibility research

**Sources checked (2026-10-31):**
- [Theme Park, Galaxy Store](https://galaxystore.samsung.com/detail/com.samsung.android.themedesigner) — says icon packs from the theme store can be applied. Says nothing explicit about Play Store packs.
- [Samsung Community: third-party icons "only through Theme Park"](https://eu.community.samsung.com/t5/mobile-apps-services/tipps-amp-tricks-how-to-return-old-oneui-6-1-icons-style-on/td-p/12331133) — a user reply, not an official statement (page not retrievable in full here).
- [One UI Glass Icon Pack listing](https://www.appgoblin.info/apps/dev.lknninex.oneuiglass) — a Play Store pack that lists "One UI Home with Theme Park" as supported, which suggests Theme Park reads standard ADW/Nova packs.
- [Lawnchair icon-pack docs](https://docs.lawnchair.app/developers/concepts/icon-packs) — the `appfilter.xml` standard.

**Conclusion:** there is no official Samsung documentation that Theme Park accepts third-party ADW/Nova packs. Community evidence says some do work. Therefore:
- The pack uses the de-facto standard (`appfilter.xml` + `drawable.xml` in `res/xml` and `assets`, `com.novalauncher.THEME`, `org.adw.launcher.THEMES` and other theme intent filters on an exported activity-alias).
- The app never claims the theme was applied. The "apply" button opens Theme Park (or its Galaxy Store page) and the in-app guide.
- If Theme Park on your phone does not list "ROB Neon", icons cannot be applied without a third-party launcher, which this project deliberately does not provide.

**Component verification.** All 63 launcher activities (Samsung + Google variants) in `app/src/main/res/xml/appfilter.xml` were checked against two public appfilter databases (Lawnicons, Arcticons): [COMPONENT_SOURCES.md](COMPONENT_SOURCES.md). This is community evidence, not a device check. The in-app **Icons** screen compares the list with the launcher activities actually installed on your phone (blue dot = matched, red dot = installed but activity differs). Apps not in the list keep their original icons.

Known gap: Samsung may rename launcher activities between One UI versions. Any red dot in Icons screen means that app needs its activity added.
