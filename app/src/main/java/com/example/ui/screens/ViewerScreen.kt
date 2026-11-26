package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.EyeCareFilter
import com.example.data.prefs.ReadingMode
import com.example.i18n.LocalStrings
import com.example.pdf.PdfPrintHelper
import com.example.pdf.PdfRenderEngine
import com.example.ui.components.PdfPageThumbnail
import com.example.ui.components.SkeletonShimmerBox
import com.example.ui.components.eyeCareColorFilter
import com.example.ui.viewmodel.ViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    onOrganizeDocument: (String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showThumbnailStrip by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.copyToDestinationUri(uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) strings.savedToDeviceSuccess else strings.errorCreatingPdf
                    )
                }
            }
        }
    }

    val canvasBackground = when (uiState.eyeCareFilter) {
        EyeCareFilter.OLED_NIGHT -> Color(0xFF0A0A0C)
        EyeCareFilter.SEPIA -> Color(0xFFEBE4D5)
        EyeCareFilter.ORIGINAL -> MaterialTheme.colorScheme.background
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackground)
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.loadError || uiState.document == null || uiState.pageCount <= 0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = strings.failedToOpenPdf,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text(strings.back)
                    }
                }
            }
            else -> {
                val doc = uiState.document!!
                val pageCount = uiState.pageCount

                var zoomScale by remember { mutableFloatStateOf(1f) }
                var panOffset by remember { mutableStateOf(Offset.Zero) }
                var settledScaleBucket by remember { mutableFloatStateOf(1f) }

                val animatedScale by animateFloatAsState(
                    targetValue = zoomScale,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "zoom_scale"
                )

                // Re-render crisper page bitmaps after zoom settles
                LaunchedEffect(zoomScale) {
                    delay(180)
                    settledScaleBucket = when {
                        zoomScale >= 2.6f -> 3.0f
                        zoomScale >= 1.75f -> 2.2f
                        zoomScale >= 1.25f -> 1.5f
                        else -> 1f
                    }
                }

                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = uiState.initialPageToRestore.coerceIn(0, pageCount - 1)
                )
                val pagerState = rememberPagerState(
                    initialPage = uiState.initialPageToRestore.coerceIn(0, pageCount - 1),
                    pageCount = { pageCount }
                )

                // Keep reading mode switches synced to current page
                LaunchedEffect(uiState.readingMode) {
                    val target = uiState.currentPage.coerceIn(0, pageCount - 1)
                    if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                        if (listState.firstVisibleItemIndex != target) {
                            listState.scrollToItem(target)
                        }
                    } else {
                        if (pagerState.currentPage != target) {
                            pagerState.scrollToPage(target)
                        }
                    }
                }

                // Observe visible page index
                LaunchedEffect(uiState.readingMode, listState) {
                    if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { idx -> viewModel.onPageChanged(idx) }
                    }
                }
                LaunchedEffect(uiState.readingMode, pagerState) {
                    if (uiState.readingMode == ReadingMode.HORIZONTAL_PAGER) {
                        snapshotFlow { pagerState.currentPage }
                            .distinctUntilChanged()
                            .collect { idx -> viewModel.onPageChanged(idx) }
                    }
                }

                // Preload current + 1 adjacent page only (strict OOM prevention)
                val density = LocalDensity.current
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(360)
                    val viewportHeightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(480)
                    val renderWidthPx = (viewportWidthPx * settledScaleBucket).roundToInt().coerceIn(360, 2400)

                    LaunchedEffect(uiState.currentPage, renderWidthPx, doc.uriString) {
                        val cur = uiState.currentPage
                        listOf(cur, cur + 1, cur - 1)
                            .filter { it in 0 until pageCount }
                            .forEach { pageIdx ->
                                viewModel.renderEngine.renderPage(
                                    uriString = doc.uriString,
                                    pageIndex = pageIdx,
                                    targetWidthPx = renderWidthPx
                                )
                            }
                    }

                    val gestureModifier = Modifier
                        .fillMaxSize()
                        .pointerInput(viewportWidthPx, viewportHeightPx) {
                            detectTapGestures(
                                onTap = { viewModel.toggleChromeVisibility() },
                                onDoubleTap = { tap ->
                                    if (zoomScale > 1.15f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        val target = 2.4f
                                        val cx = viewportWidthPx / 2f
                                        val cy = viewportHeightPx / 2f
                                        val maxX = viewportWidthPx * (target - 1f) / 2f
                                        val maxY = viewportHeightPx * (target - 1f) / 2f
                                        zoomScale = target
                                        panOffset = Offset(
                                            x = ((cx - tap.x) * (target - 1f)).coerceIn(-maxX, maxX),
                                            y = ((cy - tap.y) * (target - 1f)).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                            )
                        }
                        .pointerInput(viewportWidthPx, viewportHeightPx) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val newScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                                        val maxX = viewportWidthPx * (newScale - 1f) / 2f
                                        val maxY = viewportHeightPx * (newScale - 1f) / 2f
                                        zoomScale = newScale
                                        panOffset = if (newScale <= 1.01f) {
                                            Offset.Zero
                                        } else {
                                            Offset(
                                                x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                            )
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (pressedCount == 1 && zoomScale > 1.03f) {
                                        val panChange = event.calculatePan()
                                        if (abs(panChange.x) > 0.5f || abs(panChange.y) > 0.5f) {
                                            val maxX = viewportWidthPx * (zoomScale - 1f) / 2f
                                            val maxY = viewportHeightPx * (zoomScale - 1f) / 2f
                                            panOffset = Offset(
                                                x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                            )
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }

                    val colorFilter = remember(uiState.eyeCareFilter) {
                        eyeCareColorFilter(uiState.eyeCareFilter)
                    }

                    Box(
                        modifier = gestureModifier
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    ) {
                        if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = zoomScale <= 1.03f,
                                contentPadding = PaddingValues(vertical = 84.dp, horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("viewer_lazy_column")
                            ) {
                                items(
                                    count = pageCount,
                                    key = { index -> "${doc.id}_p_$index" }
                                ) { pageIndex ->
                                    PdfPageViewportCard(
                                        uriString = doc.uriString,
                                        pageIndex = pageIndex,
                                        renderEngine = viewModel.renderEngine,
                                        targetWidthPx = renderWidthPx,
                                        colorFilter = colorFilter,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                userScrollEnabled = zoomScale <= 1.03f,
                                pageSpacing = 12.dp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 76.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("viewer_horizontal_pager")
                            ) { pageIndex ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PdfPageViewportCard(
                                        uriString = doc.uriString,
                                        pageIndex = pageIndex,
                                        renderEngine = viewModel.renderEngine,
                                        targetWidthPx = renderWidthPx,
                                        colorFilter = colorFilter,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Floating Translucent Top Pill Bar
                    AnimatedVisibility(
                        visible = uiState.isChromeVisible,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        val isBookmarked = uiState.bookmarks.contains(uiState.currentPage)
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = CircleShape,
                            tonalElevation = 4.dp,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("viewer_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = strings.back
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = doc.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = strings.pageIndicator(uiState.currentPage + 1, pageCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggleCurrentPageBookmark()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("viewer_bookmark_button")
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = if (isBookmarked) strings.removeBookmark else strings.bookmarkPage,
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = { showOptionsSheet = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("viewer_options_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = strings.viewerOptions
                                    )
                                }
                            }
                        }
                    }

                    // Floating Translucent Bottom Scrubber & Strip
                    AnimatedVisibility(
                        visible = uiState.isChromeVisible,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (zoomScale > 1.06f) {
                                AssistChip(
                                    onClick = {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    },
                                    label = { Text(strings.zoomReset) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.ZoomOutMap,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }

                            AnimatedVisibility(visible = showThumbnailStrip) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    shape = MaterialTheme.shapes.large,
                                    tonalElevation = 4.dp,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 720.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(pageCount, key = { "thumb_$it" }) { idx ->
                                            val isSelected = idx == uiState.currentPage
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        scope.launch {
                                                            if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                                                                listState.scrollToItem(idx)
                                                            } else {
                                                                pagerState.scrollToPage(idx)
                                                            }
                                                            viewModel.onPageChanged(idx)
                                                        }
                                                    }
                                            ) {
                                                PdfPageThumbnail(
                                                    uriString = doc.uriString,
                                                    pageIndex = idx,
                                                    renderEngine = viewModel.renderEngine,
                                                    contentDescription = "${idx + 1}",
                                                    targetWidthPx = 140,
                                                    modifier = Modifier
                                                        .size(width = 52.dp, height = 72.dp)
                                                        .then(
                                                            if (isSelected) {
                                                                Modifier.border(
                                                                    2.dp,
                                                                    MaterialTheme.colorScheme.primary,
                                                                    MaterialTheme.shapes.small
                                                                )
                                                            } else Modifier
                                                        )
                                                )
                                                Text(
                                                    text = "${idx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = CircleShape,
                                tonalElevation = 4.dp,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { showThumbnailStrip = !showThumbnailStrip },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("viewer_thumbnails_toggle")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ViewCarousel,
                                            contentDescription = strings.jumpToPage,
                                            tint = if (showThumbnailStrip) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }

                                    if (pageCount > 1) {
                                        Slider(
                                            value = uiState.currentPage.toFloat(),
                                            onValueChange = { raw ->
                                                val idx = raw.roundToInt().coerceIn(0, pageCount - 1)
                                                scope.launch {
                                                    if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                                                        listState.scrollToItem(idx)
                                                    } else {
                                                        pagerState.scrollToPage(idx)
                                                    }
                                                    viewModel.onPageChanged(idx)
                                                }
                                            },
                                            valueRange = 0f..(pageCount - 1).toFloat(),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp)
                                                .testTag("viewer_page_slider")
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    IconButton(
                                        onClick = {
                                            val nextMode = if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                                                ReadingMode.HORIZONTAL_PAGER
                                            } else {
                                                ReadingMode.VERTICAL_CONTINUOUS
                                            }
                                            viewModel.setReadingMode(nextMode)
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("viewer_mode_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                                                Icons.Outlined.SwapVert
                                            } else {
                                                Icons.Outlined.SwapHoriz
                                            },
                                            contentDescription = strings.readingMode
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val filters = EyeCareFilter.entries
                                            val next = filters[(uiState.eyeCareFilter.ordinal + 1) % filters.size]
                                            viewModel.setEyeCareFilter(next)
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("viewer_eyecare_toggle")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Brightness4,
                                            contentDescription = strings.eyeCareFilter,
                                            tint = if (uiState.eyeCareFilter != EyeCareFilter.ORIGINAL) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 80.dp)
                    )
                }

                if (showOptionsSheet) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showOptionsSheet = false },
                        sheetState = sheetState
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.viewerOptions,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                IconButton(onClick = { showOptionsSheet = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = strings.close)
                                }
                            }

                            // Reading Mode
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = strings.readingMode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS,
                                        onClick = { viewModel.setReadingMode(ReadingMode.VERTICAL_CONTINUOUS) },
                                        label = { Text(strings.continuousScroll) }
                                    )
                                    FilterChip(
                                        selected = uiState.readingMode == ReadingMode.HORIZONTAL_PAGER,
                                        onClick = { viewModel.setReadingMode(ReadingMode.HORIZONTAL_PAGER) },
                                        label = { Text(strings.singlePageSwipe) }
                                    )
                                }
                            }

                            // Eye-Care Filters
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = strings.eyeCareFilter,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = uiState.eyeCareFilter == EyeCareFilter.ORIGINAL,
                                        onClick = { viewModel.setEyeCareFilter(EyeCareFilter.ORIGINAL) },
                                        label = { Text(strings.filterOriginal) }
                                    )
                                    FilterChip(
                                        selected = uiState.eyeCareFilter == EyeCareFilter.SEPIA,
                                        onClick = { viewModel.setEyeCareFilter(EyeCareFilter.SEPIA) },
                                        label = { Text(strings.filterSepia) }
                                    )
                                    FilterChip(
                                        selected = uiState.eyeCareFilter == EyeCareFilter.OLED_NIGHT,
                                        onClick = { viewModel.setEyeCareFilter(EyeCareFilter.OLED_NIGHT) },
                                        label = { Text(strings.filterOledNight) }
                                    )
                                }
                            }

                            // Bookmarks Jump List
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = strings.bookmarks,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.bookmarks.isEmpty()) {
                                    Text(
                                        text = strings.noBookmarksYet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(uiState.bookmarks.sorted()) { bmPage ->
                                            AssistChip(
                                                onClick = {
                                                    showOptionsSheet = false
                                                    scope.launch {
                                                        if (uiState.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
                                                            listState.scrollToItem(bmPage)
                                                        } else {
                                                            pagerState.scrollToPage(bmPage)
                                                        }
                                                        viewModel.onPageChanged(bmPage)
                                                    }
                                                },
                                                label = { Text("${strings.pagesShort(1).substringBefore(" ").ifEmpty { "" }} #${bmPage + 1}") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.Bookmark,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            // Document Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showOptionsSheet = false
                                        viewModel.prepareShareUri { shareUri ->
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                putExtra(Intent.EXTRA_SUBJECT, doc.displayName)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, strings.share))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.share, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showOptionsSheet = false
                                        PdfPrintHelper.printPdf(
                                            context = context,
                                            uri = Uri.parse(doc.uriString),
                                            jobName = doc.displayName
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Print, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.print, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showOptionsSheet = false
                                        renameText = doc.displayName.removeSuffix(".pdf")
                                        showRenameDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.DriveFileRenameOutline, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.rename, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showOptionsSheet = false
                                        saveAsLauncher.launch(doc.displayName)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.saveAs, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            Button(
                                onClick = {
                                    showOptionsSheet = false
                                    onOrganizeDocument(doc.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.AutoAwesomeMotion, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.organizeThisPdf)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                if (showRenameDialog) {
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text(strings.renameDocumentTitle) },
                        text = {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                label = { Text(strings.documentNameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (renameText.isNotBlank()) {
                                        viewModel.renameDocument(renameText)
                                    }
                                    showRenameDialog = false
                                }
                            ) { Text(strings.save) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) { Text(strings.cancel) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageViewportCard(
    uriString: String,
    pageIndex: Int,
    renderEngine: PdfRenderEngine,
    targetWidthPx: Int,
    colorFilter: androidx.compose.ui.graphics.ColorFilter?,
    modifier: Modifier = Modifier
) {
    var aspectRatio by remember(uriString, pageIndex) { mutableFloatStateOf(0.707f) }
    var bitmap by remember(uriString, pageIndex, targetWidthPx) {
        mutableStateOf(renderEngine.getCachedPage(uriString, pageIndex, targetWidthPx))
    }

    LaunchedEffect(uriString, pageIndex) {
        aspectRatio = renderEngine.getPageAspectRatio(uriString, pageIndex).coerceIn(0.35f, 2.5f)
    }

    LaunchedEffect(uriString, pageIndex, targetWidthPx) {
        val current = bitmap
        if (current == null || current.isRecycled || abs(current.width - targetWidthPx) > 260) {
            val rendered = renderEngine.renderPage(
                uriString = uriString,
                pageIndex = pageIndex,
                targetWidthPx = targetWidthPx
            )
            if (rendered != null) {
                bitmap = rendered
            }
        }
    }

    Surface(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        color = Color.White,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        val bmp: Bitmap? = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SkeletonShimmerBox(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.small
            )
        }
    }
}
