package com.rob.neon.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.neon.BuildConfig
import com.rob.neon.R
import com.rob.neon.data.IconEntry
import com.rob.neon.data.IconStatus
import com.rob.neon.data.Match
import com.rob.neon.system.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- helpers
private fun Context.drawableId(name: String) = resources.getIdentifier(name, "drawable", packageName)

@Composable
private fun rememberWallpaper(kind: String, sample: Int = 2): ImageBitmap? {
    val ctx = LocalContext.current
    val s by produceState<ImageBitmap?>(null, kind) {
        value = withContext(Dispatchers.IO) {
            ctx.assets.open("wallpapers/$kind.jpg").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }
        }
    }
    return s
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(Panel)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Steel)

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

// ---------------------------------------------------------------- Home
@Composable
fun HomeScreen(catalog: List<IconEntry>, statuses: List<IconStatus>, lang: String, onHowTo: () -> Unit) {
    val ctx = LocalContext.current
    var kind by rememberSaveable { mutableStateOf("home") }
    var openFailed by remember { mutableStateOf(false) }
    val installed = statuses.count { it.match != Match.NOT_INSTALLED }
    val matched = statuses.count { it.match == Match.MATCHED }
    Page {
        Title(stringResource(R.string.home_title))
        Body(stringResource(R.string.home_subtitle))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == "home", { kind = "home" }, { Text(stringResource(R.string.preview_home)) })
            FilterChip(kind == "lock", { kind = "lock" }, { Text(stringResource(R.string.preview_lock)) })
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhonePreview(kind, catalog, Modifier.widthIn(max = 280.dp).fillMaxWidth())
        }
        Body(stringResource(R.string.preview_caption))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_icons_count, catalog.size), color = Steel, fontWeight = FontWeight.Medium)
                Body(stringResource(R.string.home_installed_match, installed, matched))
                Body(stringResource(R.string.honest_note))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { openFailed = !(Actions.openApp(ctx, Actions.THEME_PARK) || Actions.openThemeParkStore(ctx)) },
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text(stringResource(R.string.btn_open_theme_park), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            OutlinedButton(onClick = onHowTo) { Text(stringResource(R.string.btn_open_howto), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (openFailed) Text(stringResource(R.string.open_failed), color = Red, fontSize = 13.sp)
    }
}

/** Mock-up assembled from the real wallpaper and icon files of the pack. No clock, no weather. */
@Composable
fun PhonePreview(kind: String, catalog: List<IconEntry>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = rememberWallpaper(kind, 4)
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(modifier.aspectRatio(1440f / 3120f).clip(shape).border(2.dp, Color2, shape).background(Graphite)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (kind == "home") {
            val cell = maxWidth / 4
            val ids = listOf("phone", "samsung_messages", "camera", "gallery", "whatsapp", "telegram", "chrome", "youtube", "settings", "calendar", "my_files", "tiktok")
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = cell * 1.25f, start = cell * 0.08f, end = cell * 0.08f)) {
                ids.chunked(4).forEach { row ->
                    Row { row.forEach { id -> Dock(ctx, id, cell * 0.84f) } }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = cell * 0.12f, start = cell * 0.08f, end = cell * 0.08f)) {
                listOf("samsung_internet", "contacts", "calculator", "clock").forEach { Dock(ctx, it, cell * 0.84f) }
            }
        }
    }
}

private val Color2 = androidx.compose.ui.graphics.Color(0xFF3A4050)

@Composable
private fun Dock(ctx: Context, id: String, size: androidx.compose.ui.unit.Dp) {
    val res = ctx.drawableId("ic_$id")
    if (res != 0) Image(painterResource(res), null, Modifier.padding(size * 0.04f).size(size))
}

// ---------------------------------------------------------------- Icons
@Composable
fun IconsScreen(statuses: List<IconStatus>, lang: String) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()
    val shown = statuses.filter { s ->
        q.isEmpty() || s.entry.ru.lowercase().contains(q) || s.entry.de.lowercase().contains(q) ||
            s.entry.id.contains(q) || s.entry.packages.any { it.contains(q) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.icons_search)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default,
        )
        Body(stringResource(R.string.diag_hint))
        if (shown.isEmpty()) Text(stringResource(R.string.icons_empty), color = Steel)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp), modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.entry.id }) { s ->
                val ctx = LocalContext.current
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { selected = s.entry.id }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Image(painterResource(ctx.drawableId(s.entry.drawableName)), s.entry.label(lang), Modifier.size(72.dp))
                        val dot = when (s.match) { Match.MATCHED -> Blue; Match.MISMATCH -> Red; Match.NOT_INSTALLED -> null }
                        if (dot != null) Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(dot).border(1.5.dp, Graphite, CircleShape))
                    }
                    Text(s.entry.label(lang), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    val sel = statuses.firstOrNull { it.entry.id == selected }
    if (sel != null) {
        val none = stringResource(R.string.detail_none)
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton({ selected = null }) { Text(stringResource(R.string.btn_close)) } },
            title = { Text(sel.entry.label(lang)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text(
                            stringResource(when (sel.match) {
                                Match.MATCHED -> R.string.status_matched
                                Match.MISMATCH -> R.string.status_mismatch
                                Match.NOT_INSTALLED -> R.string.status_not_installed
                            }), color = if (sel.match == Match.MISMATCH) Red else Steel, fontWeight = FontWeight.Medium,
                        )
                    }
                    item { Label(stringResource(R.string.detail_packages)); Mono(sel.entry.packages.joinToString("\n")) }
                    item { Label(stringResource(R.string.detail_components)); Mono(sel.entry.components.joinToString("\n")) }
                    item { Label(stringResource(R.string.detail_launchers)); Mono(sel.launchers.joinToString("\n").ifEmpty { none }) }
                }
            },
        )
    }
}

@Composable private fun Label(t: String) = Text(t, fontSize = 12.sp, color = Blue, fontWeight = FontWeight.SemiBold)
@Composable private fun Mono(t: String) = Text(t, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ---------------------------------------------------------------- Wallpapers
@Composable
fun WallpapersScreen() {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf(false) }
    Page {
        Title(stringResource(R.string.tab_wallpapers))
        Body(stringResource(R.string.wp_hint))
        listOf("home" to (R.string.wp_home_title to R.string.wp_home_desc), "lock" to (R.string.wp_lock_title to R.string.wp_lock_desc)).forEach { (kind, t) ->
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(t.first), fontWeight = FontWeight.SemiBold, color = Steel)
                    Body(stringResource(t.second))
                    val bmp = rememberWallpaper(kind, 4)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.height(320.dp).aspectRatio(1440f / 3120f).clip(RoundedCornerShape(18.dp)).border(1.dp, Color2, RoundedCornerShape(18.dp)).background(Graphite)) {
                            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                    }
                    Button(
                        onClick = { error = !Actions.cropAndSet(ctx, kind) },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue), modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.wp_btn_crop)) }
                    OutlinedButton(onClick = { error = !Actions.attachChooser(ctx, kind) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wp_btn_chooser))
                    }
                }
            }
        }
        TextButton(onClick = { error = !Actions.systemWallpaperPicker(ctx) }) { Text(stringResource(R.string.wp_btn_picker)) }
        if (error) Text(stringResource(R.string.wp_error), color = Red, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- How to apply
@Composable
fun HowToScreen(onWallpapers: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("rob_neon", Context.MODE_PRIVATE) }
    // Checklist state is persisted so it survives app updates signed with the same key.
    val done = remember { mutableStateOf((1..6).filter { prefs.getBoolean("step_$it", false) }.toSet()) }
    var failed by remember { mutableStateOf(false) }
    val themePark = remember { Actions.isInstalled(ctx, Actions.THEME_PARK) }
    val goodLock = remember { Actions.isInstalled(ctx, Actions.GOOD_LOCK) }
    val steps = listOf(
        R.string.howto_s1_t to R.string.howto_s1_d, R.string.howto_s2_t to R.string.howto_s2_d,
        R.string.howto_s3_t to R.string.howto_s3_d, R.string.howto_s4_t to R.string.howto_s4_d,
        R.string.howto_s5_t to R.string.howto_s5_d, R.string.howto_s6_t to R.string.howto_s6_d,
    )
    Page {
        Title(stringResource(R.string.howto_title))
        Body(stringResource(R.string.howto_warn))
        Text(
            stringResource(if (themePark) R.string.theme_park_installed else R.string.theme_park_missing),
            color = if (themePark) Blue else Red, fontWeight = FontWeight.Medium, fontSize = 14.sp,
        )
        steps.forEachIndexed { i, (t, d) ->
            val n = i + 1
            Card(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = n in done.value, onCheckedChange = { c ->
                        done.value = if (c) done.value + n else done.value - n
                        prefs.edit().putBoolean("step_$n", c).apply()
                    })
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(t), fontWeight = FontWeight.SemiBold, color = Steel)
                        Body(stringResource(d))
                        when (n) {
                            1 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ failed = !Actions.openThemeParkStore(ctx) }) { Text(stringResource(R.string.btn_open_galaxy_store), fontSize = 12.sp) }
                                if (goodLock) OutlinedButton({ failed = !Actions.openApp(ctx, Actions.GOOD_LOCK) }) { Text(stringResource(R.string.btn_open_good_lock), fontSize = 12.sp) }
                            }
                            2 -> Button({ failed = !(Actions.openApp(ctx, Actions.THEME_PARK) || Actions.openThemeParkStore(ctx)) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                                Text(stringResource(R.string.btn_open_theme_park))
                            }
                            5 -> OutlinedButton(onWallpapers) { Text(stringResource(R.string.btn_go_wallpapers)) }
                        }
                    }
                }
            }
        }
        if (failed) Text(stringResource(R.string.open_failed), color = Red, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- About
@Composable
fun AboutScreen() {
    Page {
        Title(stringResource(R.string.about_title))
        Text(stringResource(R.string.app_name), color = Steel, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = Steel)
        Body(stringResource(R.string.about_build))
        Body(stringResource(R.string.about_privacy))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.about_limits_title), fontWeight = FontWeight.SemiBold, color = Steel)
                listOf(R.string.about_limit_1, R.string.about_limit_2, R.string.about_limit_3, R.string.about_limit_4).forEach { Body("• " + stringResource(it)) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.about_checked_title), fontWeight = FontWeight.SemiBold, color = Steel)
                Body(stringResource(R.string.about_checked_body))
            }
        }
    }
}
