package de.spardirekt.recipeveo.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.model.ProjectStatus
import de.spardirekt.recipeveo.ui.components.ApiKeyDialog
import de.spardirekt.recipeveo.ui.components.AppHeader
import de.spardirekt.recipeveo.ui.components.GradientHeading
import de.spardirekt.recipeveo.ui.components.VppTags
import de.spardirekt.recipeveo.ui.theme.LocalBottomBarInset
import de.spardirekt.recipeveo.ui.theme.LocalVppType
import de.spardirekt.recipeveo.ui.theme.VppColors
import de.spardirekt.recipeveo.ui.theme.VppDimens
import de.spardirekt.recipeveo.ui.theme.VppLayout

/**
 * History and Settings are reachable from the bottom navigation, so the header
 * here carries only the "new project" action instead of repeating both tabs.
 */
@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    onOpenResult: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val type = LocalVppType.current
    val density = LocalDensity.current
    val bottomBarInset = LocalBottomBarInset.current
    var actionBarHeight by remember { mutableStateOf(0.dp) }

    LaunchedEffect(Unit) { viewModel.bootstrap() }
    LaunchedEffect(state.navigateToResultId) {
        state.navigateToResultId?.let {
            viewModel.consumeNavigation()
            onOpenResult(it)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        VppColors.backgroundLight,
                        VppColors.backgroundGlow.copy(alpha = 0.55f),
                        VppColors.backgroundLight
                    )
                )
            )
            .testTag(VppTags.CREATE_SCREEN)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VppDimens.screenPadding)
                .padding(
                    top = VppLayout.screenTopPadding,
                    bottom = bottomBarInset + actionBarHeight + VppLayout.floatingContentGap
                )
        ) {
            AppHeader(onNewProject = { viewModel.newProject() })
            Spacer(Modifier.height(18.dp))
            GradientHeading("Генератор промптов для видео")
            Spacer(Modifier.height(8.dp))
            Text(
                "Загружайте фото товара, выбирайте настройки\nи получайте готовый промпт для VEO 3.1.",
                style = type.secondary.copy(color = VppColors.textMutedDark, lineHeight = 20.sp)
            )
            Spacer(Modifier.height(20.dp))

            PhotosSection(
                images = state.images,
                onAddPhotos = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemovePhoto = viewModel::removeImage
            )

            Spacer(Modifier.height(VppLayout.sectionGap))
            OptionalWishSection(
                expanded = state.wishExpanded,
                text = state.optionalWish,
                onToggle = viewModel::toggleWish,
                onTextChange = viewModel::setWish
            )

            Spacer(Modifier.height(VppLayout.sectionGap))
            VoiceSection(voice = state.voice, onVoiceChange = viewModel::setVoice)

            Spacer(Modifier.height(VppLayout.sectionGap))
            ModeSection(
                mode = state.mode,
                creative = state.creative,
                tiktokShopMode = state.tiktokShopMode,
                onModeChange = viewModel::setMode,
                onCreativeChange = viewModel::setCreative,
                onTiktokChange = viewModel::setTiktok
            )

            if (GenerationProgress.showsRail(state.stage, state.isGenerating)) {
                Spacer(Modifier.height(VppLayout.sectionGap))
                GenerationProgressCard(state.stage)
            }

            if (state.errorMessage.isNotBlank() &&
                state.project?.status == ProjectStatus.Error.name
            ) {
                Spacer(Modifier.height(VppLayout.sectionGap))
                GenerationErrorCard(
                    message = state.errorMessage,
                    detail = state.errorDetail,
                    showDetail = state.showErrorDetail,
                    onContinue = viewModel::continueGeneration,
                    onDetails = viewModel::toggleErrorDetail
                )
            } else if (state.errorMessage.isNotBlank() && !state.isGenerating) {
                Spacer(Modifier.height(12.dp))
                Text(state.errorMessage, color = VppColors.error, style = type.secondary)
            }
        }

        CreateActionBar(
            isGenerating = state.isGenerating,
            stage = state.stage,
            photoCount = state.images.size,
            onGenerate = viewModel::onGenerate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarInset)
                .onSizeChanged { actionBarHeight = with(density) { it.height.toDp() } }
        )
    }

    if (state.showApiKeyDialog) {
        ApiKeyDialog(
            value = state.apiKeyInput,
            onValueChange = viewModel::setApiKeyInput,
            onSave = viewModel::saveApiKeyAndContinue,
            onCancel = viewModel::dismissApiDialog
        )
    }
}

private const val MAX_PHOTOS = 15
