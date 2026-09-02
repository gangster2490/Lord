package de.spardirekt.recipeveo.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.spardirekt.recipeveo.model.ImageCategory
import de.spardirekt.recipeveo.model.ProjectImage
import de.spardirekt.recipeveo.ui.theme.LocalVppType
import de.spardirekt.recipeveo.ui.theme.VppColors
import de.spardirekt.recipeveo.ui.theme.VppShapes

@Composable
fun PhotoThumb(
    img: ProjectImage,
    index: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalVppType.current
    val position = index + 1
    Box(modifier = modifier.aspectRatio(1f)) {
        AsyncImage(
            model = img.localPath?.takeIf { it.isNotBlank() } ?: img.uri,
            contentDescription = "Фото $position",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(VppShapes.thumbShape)
        )
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(VppColors.cardInset.copy(alpha = 0.85f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$position",
                color = VppColors.textLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .align(Alignment.TopEnd)
                .clickable(onClickLabel = "Удалить фото $position", onClick = onRemove)
                .semantics {
                    role = Role.Button
                    contentDescription = "Удалить фото $position"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        if (img.category != ImageCategory.UNKNOWN) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VppColors.accentPurple.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    img.category.badgeLabel(),
                    style = type.badge.copy(color = Color.White, fontSize = 9.sp),
                    maxLines = 1
                )
            }
        }
    }
}
