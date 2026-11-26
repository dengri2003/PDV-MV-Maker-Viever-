package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.data.prefs.EyeCareFilter
import com.example.data.prefs.ImageEnhancementFilter
import com.example.pdf.PdfRenderEngine

@Composable
fun SkeletonShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_shift"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift - 350f, shift - 350f),
        end = Offset(shift, shift)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun PdfPageThumbnail(
    uriString: String,
    pageIndex: Int,
    renderEngine: PdfRenderEngine,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 220,
    shape: Shape = MaterialTheme.shapes.small,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(uriString, pageIndex, targetWidthPx) {
        mutableStateOf(renderEngine.getCachedThumbnail(uriString, pageIndex, targetWidthPx))
    }
    var failed by remember(uriString, pageIndex) { mutableStateOf(false) }

    LaunchedEffect(uriString, pageIndex, targetWidthPx) {
        if (bitmap == null || bitmap?.isRecycled == true) {
            failed = false
            val rendered = renderEngine.renderThumbnail(uriString, pageIndex, targetWidthPx)
            if (rendered != null) {
                bitmap = rendered
            } else {
                failed = true
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center
    ) {
        val currentBmp: Bitmap? = bitmap
        when {
            currentBmp != null && !currentBmp.isRecycled -> {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            failed -> {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                SkeletonShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)
            }
        }
    }
}

fun eyeCareColorFilter(filter: EyeCareFilter): ColorFilter? {
    return when (filter) {
        EyeCareFilter.ORIGINAL -> null
        EyeCareFilter.SEPIA -> {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.96f, 0.04f, 0.00f, 0f, 10f,
                        0.02f, 0.90f, 0.02f, 0f, 4f,
                        0.00f, 0.04f, 0.74f, 0f, -4f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        EyeCareFilter.OLED_NIGHT -> {
            // Smart inversion: pure white paper (#FFFFFF) maps to #0E0E11 OLED dark, dark text maps to #ECEAE4
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.91f, 0f, 0f, 0f, 242f,
                        0f, -0.91f, 0f, 0f, 240f,
                        0f, 0f, -0.89f, 0f, 236f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }
}

fun imageEnhancementColorFilter(filter: ImageEnhancementFilter): ColorFilter? {
    if (filter == ImageEnhancementFilter.ORIGINAL) return null
    val androidMatrix = com.example.pdf.PdfGeneratorEngine.createEnhancementMatrix(filter)
    return ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array))
}
