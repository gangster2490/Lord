package de.rob.servermobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val background = Color(0xFF060B16)
private val panel = Color(0xFF131F33)
private val cyan = Color(0xFF17B9FF)
private val blue = Color(0xFF176BFF)
private val muted = Color(0xFFA6B6D0)
private val white = Color(0xFFF4F8FF)
private val outline = Color(0xFF254365)

data class RobService(val initials: String, val name: String, val description: String, val category: String, val accent: Color)
private val services = listOf(
    RobService("RT", "ROB Terminal Viewer", "Результаты команд терминала", "Система", Color(0xFF00C88B)),
    RobService("CM", "Claude Monitor", "Статистика Claude Code", "AI", Color(0xFF9855F7)),
    RobService("RH", "ROB Hub", "Фото и промпты для AI", "AI", Color(0xFF238FFF)),
    RobService("CA", "Claude-агент", "Управление сервером", "AI", Color(0xFFFF943B)),
    RobService("TS", "TikTok Studio", "Создание рекламных видео", "Видео", Color(0xFFFF368F)),
    RobService("DC", "Docker Manager", "Контейнеры и журналы", "Система", Color(0xFF04C9E9)),
    RobService("FS", "Файлы", "Файлы сервера", "Система", Color(0xFF4F9AFF)),
    RobService("LB", "Учебные приложения", "Обучающие сервисы", "Обучение", Color(0xFFB461FF))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RobMobile() }
    }
}

@Composable
private fun RobMobile() {
    var tab by remember { mutableStateOf("Главная") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Все") }
    var german by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("SparDirekt DE") }
    var host by remember { mutableStateOf("100.79.80.15") }
    var port by remember { mutableStateOf("8099") }
    var selected by remember { mutableStateOf<RobService?>(null) }
    val nav = listOf("Главная", "Приложения", "TikTok", "Терминал", "Настройки")
    MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = background, surface = panel, onSurface = white)) {
        Column(Modifier.fillMaxSize().background(background)) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF10213B), background))).padding(18.dp)) {
                Text("ROB Server", color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("CONTROL CENTER  •  PREMIUM", color = cyan, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text("● Статус не проверен · подключение не настроено", color = Color(0xFFFFC778), fontSize = 12.sp)
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    "Главная", "Приложения" -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (tab == "Главная") {
                            item { ServerCard(host, port) }
                            item { Text("Мои приложения", color = white, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                        }
                        item {
                            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Поиск приложений") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                listOf("Все", "AI", "Видео", "Система", "Обучение").forEach { category ->
                                    FilterChip(selected = filter == category, onClick = { filter = category }, label = { Text(category) })
                                }
                            }
                        }
                        val visible = services.filter { (filter == "Все" || it.category == filter) &&
                            (it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)) }
                        items(visible.chunked(2)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { service -> Box(Modifier.weight(1f)) { ServiceCard(service) { selected = service } } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        item { Text("Список демонстрационный. Статусы и адреса сервисов будут получены из API сервера.", color = muted, fontSize = 12.sp) }
                    }
                    "TikTok" -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item { Text("TikTok Studio", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                        item { Text("Выбери профиль. Подключение и публикация пока не активны.", color = muted) }
                        items(listOf("SparDirekt DE", "Вкусно", "Другой аккаунт")) { name ->
                            Card(onClick = { account = name }, colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (account == name) "◉" else "○", color = cyan, fontSize = 24.sp)
                                    Spacer(Modifier.width(14.dp)); Text(name, color = white, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                    "Терминал" -> Column(Modifier.padding(20.dp)) {
                        Text("ROB Terminal", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Text("Терминал отключён до настройки защищённого API. Команды не выполняются.", color = muted)
                    }
                    "Настройки" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Настройки", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(host, { host = it }, label = { Text("IP или домен сервера") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Порт") }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Deutsch", color = white); Spacer(Modifier.weight(1f)); Switch(german, { german = it }) }
                        Text("Настройки хранятся только в памяти приложения до следующего запуска. HTTP-запросы отключены.", color = muted, fontSize = 12.sp)
                        Text(if (german) "Sprache: Deutsch (Übersetzung folgt)" else "Язык: Русский", color = cyan)
                    }
                }
            }
            NavigationBar(containerColor = Color(0xFF101A2B)) {
                nav.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label }, icon = {
                        Text(listOf("⌂", "▦", "▶", ">_", "⚙")[i], fontSize = 23.sp)
                    }, label = { Text(label, fontSize = 10.sp, maxLines = 1) })
                }
            }
        }
        if (selected != null) AlertDialog(onDismissRequest = { selected = null },
            title = { Text(selected!!.name) },
            text = { Text("Это демонстрационная карточка. Для открытия сервиса нужно подключить проверенный адрес и авторизацию ROB Server.") },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Понятно") } })
    }
}

@Composable
private fun ServerCard(host: String, port: String) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), border = BorderStroke(1.dp, outline), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("▣  rob-server", color = white, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Debian 13  •  $host:$port", color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("CPU", "RAM", "SWAP").forEach { name ->
                    Column(Modifier.weight(1f).background(Color(0xFF0A1425), RoundedCornerShape(14.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, color = cyan, fontSize = 12.sp)
                        Text("—", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Нет телеметрии · показатели появятся после подключения", color = muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ServiceCard(service: RobService, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = panel),
        border = BorderStroke(1.dp, outline), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(218.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(50.dp).background(service.accent, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Text(service.initials, color = white, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text(service.name, color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(service.description, color = muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("● Не проверен", color = Color(0xFFFFC778), fontSize = 11.sp)
            Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(blue, cyan)), RoundedCornerShape(12.dp)).padding(9.dp), contentAlignment = Alignment.Center) {
                Text("Подробнее →", color = white, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
