package com.rob.neon.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.rob.neon.R
import com.rob.neon.data.Catalog
import com.rob.neon.data.Diagnostics

@Composable
fun RobNeonApp() {
    val ctx = LocalContext.current
    val lang = LocalConfiguration.current.locales[0].language
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val catalog = remember { Catalog.load(ctx) }
    // Diagnostics read the installed-app list of this phone; recomputed when the activity is recreated.
    val statuses = remember { Diagnostics.scan(ctx, catalog) }

    val tabs = listOf(
        Triple(R.string.tab_home, Icons.Filled.Home, 0),
        Triple(R.string.tab_icons, Icons.Filled.Search, 1),
        Triple(R.string.tab_wallpapers, Icons.Filled.Star, 2),
        Triple(R.string.tab_howto, Icons.Filled.Build, 3),
        Triple(R.string.tab_about, Icons.Filled.Info, 4),
    )
    Scaffold(
        containerColor = Graphite,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                tabs.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PanelHi),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(catalog, statuses, lang, onHowTo = { tab = 3 })
                1 -> IconsScreen(statuses, lang)
                2 -> WallpapersScreen()
                3 -> HowToScreen(onWallpapers = { tab = 2 })
                else -> AboutScreen()
            }
        }
    }
}
