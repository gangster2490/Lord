package de.spardirekt.tiktokshop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.tiktokshop.data.CreatorOptions
import de.spardirekt.tiktokshop.ui.components.Hint
import de.spardirekt.tiktokshop.ui.components.SectionLabel
import de.spardirekt.tiktokshop.ui.components.ShopCard
import de.spardirekt.tiktokshop.ui.components.ShopDropdown
import de.spardirekt.tiktokshop.ui.components.ShopTextField
import de.spardirekt.tiktokshop.ui.creator.CreatorViewModel
import de.spardirekt.tiktokshop.ui.theme.TextMid
import de.spardirekt.tiktokshop.ui.theme.TextPrimary
import de.spardirekt.tiktokshop.ui.veo.VeoViewModel

@Composable
fun SettingsScreen(
    creatorViewModel: CreatorViewModel,
    veoViewModel: VeoViewModel,
) {
    val creator by creatorViewModel.state.collectAsStateWithLifecycle()
    val veo by veoViewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Einstellungen", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text("Keys und Endpunkte bleiben lokal auf diesem Gerät.", color = TextMid)

        ShopCard {
            SectionLabel("Anthropic API Key")
            ShopTextField(
                value = creator.apiKey,
                onValueChange = creatorViewModel::onApiKeyChange,
                placeholder = "sk-ant-api03-...",
                password = true,
            )
            Hint("Wird nur an den konfigurierten Proxy weitergeleitet (x-api-key-fwd).")
        }

        ShopCard {
            SectionLabel("Proxy URL")
            ShopTextField(
                value = creator.proxyUrl,
                onValueChange = creatorViewModel::onProxyChange,
                placeholder = "http://10.0.2.2:3001",
            )
            Hint("Emulator: 10.0.2.2 = Host-localhost. Physisches Gerät: LAN-IP des Proxy-Servers.")
        }

        ShopCard {
            SectionLabel("Creator-Voreinstellungen")
            ShopDropdown("Video-Stil", creator.videoStyle, CreatorOptions.videoStyles, creatorViewModel::onStyleChange)
            ShopDropdown("Ton", creator.tone, CreatorOptions.tones, creatorViewModel::onToneChange)
        }

        ShopCard {
            SectionLabel("OpenAI API Key (VEO Cleaner)")
            ShopTextField(
                value = veo.apiKey,
                onValueChange = veoViewModel::onApiKeyChange,
                placeholder = "sk-...",
                password = true,
            )
            ShopDropdown(
                "Analyse-Modell",
                veo.analysisModel,
                listOf("gpt-4o", "gpt-4o-mini"),
                veoViewModel::onAnalysisModel,
            )
            ShopDropdown(
                "Bildgenerierung",
                veo.imageModel,
                listOf("dall-e-3", "dall-e-2"),
                veoViewModel::onImageModel,
            )
        }
    }
}
