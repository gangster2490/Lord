"""Catalog: 30 icons -> package / launcher-activity mappings.

Every component below is checked by verify_components.py against two independent
community-maintained appfilter databases (Lawnicons, Arcticons). Nothing is guessed:
components that are not present in at least one database are rejected by the build.
"""
SAM, GOO = "samsung", "google"

# id: (RU, DE, [(package, [classes...])])
APPS = {
 "phone": ("Телефон", "Telefon", [
    ("com.samsung.android.dialer", ["com.samsung.android.dialer.DialtactsActivity"]),
    ("com.google.android.dialer", ["com.google.android.dialer.DialtactsActivity", "com.android.dialer.main.impl.MainActivity", "com.google.android.apps.dialer.extensions.GoogleDialtactsActivity"])]),
 "contacts": ("Контакты", "Kontakte", [
    ("com.samsung.android.app.contacts", ["com.samsung.android.contacts.contactslist.PeopleActivity"]),
    ("com.google.android.contacts", ["com.android.contacts.activities.PeopleActivity"])]),
 "samsung_messages": ("Samsung Messages", "Samsung Nachrichten", [
    ("com.samsung.android.messaging", ["com.samsung.android.messaging.ui.ConversationListActivity", "com.samsung.android.messaging.ui.ConversationList", "com.android.mms.ui.ConversationComposer"])]),
 "google_messages": ("Google Messages", "Google Nachrichten", [
    ("com.google.android.apps.messaging", ["com.google.android.apps.messaging.ui.ConversationListActivity", "com.google.android.apps.messaging.main.MainActivity"])]),
 "camera": ("Камера", "Kamera", [
    ("com.sec.android.app.camera", ["com.sec.android.app.camera.Camera"]),
    ("com.google.android.GoogleCamera", ["com.android.camera.CameraLauncher", "com.android.camera.CameraActivity"])]),
 "gallery": ("Галерея", "Galerie", [
    ("com.sec.android.gallery3d", ["com.sec.android.gallery3d.app.GalleryActivity", "com.sec.android.gallery3d.app.Gallery", "com.samsung.android.gallery.app.activity.GalleryActivity"])]),
 "settings": ("Настройки", "Einstellungen", [
    ("com.android.settings", ["com.android.settings.Settings", "com.android.settings.MainSettings"])]),
 "calendar": ("Календарь", "Kalender", [
    ("com.samsung.android.calendar", ["com.samsung.android.app.calendar.activity.MainActivity", "com.android.calendar.AllInOneActivity"]),
    ("com.google.android.calendar", ["com.android.calendar.AllInOneActivity"])]),
 "clock": ("Часы", "Uhr", [
    ("com.sec.android.app.clockpackage", ["com.sec.android.app.clockpackage.ClockPackage"]),
    ("com.google.android.deskclock", ["com.android.deskclock.DeskClock"])]),
 "my_files": ("Мои файлы", "Eigene Dateien", [
    ("com.sec.android.app.myfiles", ["com.sec.android.app.myfiles.external.ui.MainActivity", "com.sec.android.app.myfiles.common.MainActivity", "com.sec.android.app.myfiles.ui.MainActivity", "com.sec.android.app.myfiles.external.ui.LaunchActivity"])]),
 "calculator": ("Калькулятор", "Rechner", [
    ("com.sec.android.app.popupcalculator", ["com.sec.android.app.popupcalculator.Calculator"]),
    ("com.google.android.calculator", ["com.android.calculator2.Calculator"])]),
 "samsung_internet": ("Samsung Internet", "Samsung Internet", [
    ("com.sec.android.app.sbrowser", ["com.sec.android.app.sbrowser.SBrowserMainActivity"])]),
 "chrome": ("Chrome", "Chrome", [
    ("com.android.chrome", ["com.google.android.apps.chrome.Main", "com.android.chrome.Main"])]),
 "gmail": ("Gmail", "Gmail", [
    ("com.google.android.gm", ["com.google.android.gm.ConversationListActivityGmail", "com.google.android.gm.GmailActivity"])]),
 "maps": ("Google Карты", "Google Maps", [
    ("com.google.android.apps.maps", ["com.google.android.maps.MapsActivity"])]),
 "play_store": ("Google Play", "Google Play", [
    ("com.android.vending", ["com.android.vending.AssetBrowserActivity", "com.google.android.finsky.activities.PlayLauncherActivity"])]),
 "galaxy_store": ("Galaxy Store", "Galaxy Store", [
    ("com.sec.android.app.samsungapps", ["com.sec.android.app.samsungapps.Main", "com.sec.android.app.samsungapps.SamsungAppsMainActivity"])]),
 "samsung_notes": ("Samsung Notes", "Samsung Notes", [
    ("com.samsung.android.app.notes", ["com.samsung.android.app.notes.memolist.MemoListActivity"])]),
 "whatsapp": ("WhatsApp", "WhatsApp", [
    ("com.whatsapp", ["com.whatsapp.Main", "com.whatsapp.HomeActivity"])]),
 "telegram": ("Telegram", "Telegram", [
    ("org.telegram.messenger", ["org.telegram.messenger.DefaultIcon", "org.telegram.messenger.ClassicIcon"])]),
 "youtube": ("YouTube", "YouTube", [
    ("com.google.android.youtube", ["com.google.android.youtube.app.honeycomb.Shell$HomeActivity"])]),
 "tiktok": ("TikTok", "TikTok", [
    ("com.zhiliaoapp.musically", ["com.ss.android.ugc.aweme.splash.SplashActivity", "com.zhiliaoapp.musically.MainActivity"]),
    ("com.ss.android.ugc.trill", ["com.ss.android.ugc.aweme.splash.SplashActivity"])]),
 "instagram": ("Instagram", "Instagram", [
    ("com.instagram.android", ["com.instagram.mainactivity.LauncherActivity", "com.instagram.android.activity.MainTabActivity"])]),
 "facebook": ("Facebook", "Facebook", [
    ("com.facebook.katana", ["com.facebook.katana.LoginActivity"])]),
 "spotify": ("Spotify", "Spotify", [
    ("com.spotify.music", ["com.spotify.music.MainActivity", "com.spotify.music.main.MainScreenActivity", "com.spotify.music.MainActivityAlias"])]),
 "chatgpt": ("ChatGPT", "ChatGPT", [
    ("com.openai.chatgpt", ["com.openai.chatgpt.MainActivity"])]),
 "google_drive": ("Google Диск", "Google Drive", [
    ("com.google.android.apps.docs", ["com.google.android.apps.docs.app.NewMainProxyActivity", "com.google.android.apps.docs.app.HomeScreenActivity"])]),
 "google_photos": ("Google Фото", "Google Fotos", [
    ("com.google.android.apps.photos", ["com.google.android.apps.photos.home.HomeActivity"])]),
 "samsung_wallet": ("Samsung Wallet", "Samsung Wallet", [
    ("com.samsung.android.spay", ["com.samsung.android.spay.ui.SpayMainActivity", "com.samsung.android.spay.InitActivity"])]),
 "good_lock": ("Good Lock", "Good Lock", [
    ("com.samsung.android.goodlock", ["com.samsung.android.goodlock.presentation.view.LaunchActivity", "com.samsung.android.goodlock.MainActivity"])]),
}
