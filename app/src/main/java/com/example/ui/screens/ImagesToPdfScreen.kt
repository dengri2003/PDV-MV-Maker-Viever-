package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.prefs.ImageEnhancementFilter
import com.example.data.prefs.PdfExportQuality
import com.example.data.prefs.PdfMarginSize
import com.example.data.prefs.PdfPageSize
import com.example.i18n.LocalStrings
import com.example.pdf.ImagePageSpec
import com.example.ui.components.imageEnhancementColorFilter
import com.example.ui.viewmodel.ImagesToPdfViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImagesToPdfScreen(
    viewModel: ImagesToPdfViewModel,
    onBack: () -> Unit,
    onOpenGeneratedPdf: (String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExportSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.addImages(listOf(uri))
        }
        pendingCameraUri = null
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.generateAndRegisterPdf(externalSaveUri = targetUri) { doc ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    snackbarHostState.showSnackbar(strings.savedToDeviceSuccess)
                }
                onOpenGeneratedPdf(doc.id)
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    fun launchCamera() {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(strings.imagesStudioTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { launchCamera() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("images_camera_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = strings.takePhoto)
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("images_add_photos_button")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = strings.addPhotos)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (uiState.pages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.isGenerating) {
                            val progress = if (uiState.progressTotal > 0) {
                                uiState.progressCurrent.toFloat() / uiState.progressTotal.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${strings.generatingPdf} (${uiState.progressCurrent}/${uiState.progressTotal})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showExportSheet = true },
                                enabled = !uiState.isGenerating,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("images_export_settings_button")
                            ) {
                                Text(strings.exportSettings, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = {
                                    viewModel.generateAndRegisterPdf { doc ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenGeneratedPdf(doc.id)
                                    }
                                },
                                enabled = !uiState.isGenerating,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("images_create_open_button")
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.generateAndOpen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (uiState.pages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 520.dp)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = strings.noImagesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = strings.noImagesSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("empty_pick_photos_button")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.addPhotos)
                        }
                        OutlinedButton(
                            onClick = { launchCamera() },
                            modifier = Modifier.testTag("empty_take_photo_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.takePhoto)
                        }
                    }
                }
            } else {
                var draggedIndex by remember { mutableIntStateOf(-1) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                val stepPx = with(LocalDensity.current) { 116.dp.toPx() }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 780.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = strings.dragToReorderHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    itemsIndexed(
                        items = uiState.pages,
                        key = { _, item -> item.id }
                    ) { index, page ->
                        val isDragging = index == draggedIndex
                        ImagePageRowCard(
                            index = index,
                            total = uiState.pages.size,
                            page = page,
                            isDragging = isDragging,
                            dragTranslationY = if (isDragging) dragOffsetY else 0f,
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedIndex = index
                                dragOffsetY = 0f
                            },
                            onDragDelta = { deltaY ->
                                dragOffsetY += deltaY
                                val currentIdx = draggedIndex
                                if (currentIdx != -1) {
                                    if (dragOffsetY > stepPx * 0.7f && currentIdx < uiState.pages.lastIndex) {
                                        viewModel.movePage(currentIdx, currentIdx + 1)
                                        draggedIndex = currentIdx + 1
                                        dragOffsetY -= stepPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (dragOffsetY < -stepPx * 0.7f && currentIdx > 0) {
                                        viewModel.movePage(currentIdx, currentIdx - 1)
                                        draggedIndex = currentIdx - 1
                                        dragOffsetY += stepPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedIndex = -1
                                dragOffsetY = 0f
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    viewModel.movePage(index, index - 1)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onMoveDown = {
                                if (index < uiState.pages.lastIndex) {
                                    viewModel.movePage(index, index + 1)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onRotate = { viewModel.rotatePage(page.id) },
                            onCycleFilter = { viewModel.cycleFilter(page.id) },
                            onDelete = { viewModel.removePage(page.id) }
                        )
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = strings.exportSettings,
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = uiState.filename,
                    onValueChange = viewModel::updateFilename,
                    label = { Text(strings.outputFilename) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.pageSize,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.pageSize == PdfPageSize.A4,
                            onClick = { viewModel.setPageSize(PdfPageSize.A4) },
                            label = { Text(strings.pageSizeA4) }
                        )
                        FilterChip(
                            selected = uiState.pageSize == PdfPageSize.LETTER,
                            onClick = { viewModel.setPageSize(PdfPageSize.LETTER) },
                            label = { Text(strings.pageSizeLetter) }
                        )
                        FilterChip(
                            selected = uiState.pageSize == PdfPageSize.FIT_IMAGE,
                            onClick = { viewModel.setPageSize(PdfPageSize.FIT_IMAGE) },
                            label = { Text(strings.pageSizeFitImage) }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.margins,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.marginSize == PdfMarginSize.NONE,
                            onClick = { viewModel.setMarginSize(PdfMarginSize.NONE) },
                            label = { Text(strings.marginNone) }
                        )
                        FilterChip(
                            selected = uiState.marginSize == PdfMarginSize.SMALL,
                            onClick = { viewModel.setMarginSize(PdfMarginSize.SMALL) },
                            label = { Text(strings.marginSmall) }
                        )
                        FilterChip(
                            selected = uiState.marginSize == PdfMarginSize.NORMAL,
                            onClick = { viewModel.setMarginSize(PdfMarginSize.NORMAL) },
                            label = { Text(strings.marginNormal) }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.compressionQuality,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.quality == PdfExportQuality.COMPACT,
                            onClick = { viewModel.setQuality(PdfExportQuality.COMPACT) },
                            label = { Text(strings.qualityCompact) }
                        )
                        FilterChip(
                            selected = uiState.quality == PdfExportQuality.BALANCED,
                            onClick = { viewModel.setQuality(PdfExportQuality.BALANCED) },
                            label = { Text(strings.qualityBalanced) }
                        )
                        FilterChip(
                            selected = uiState.quality == PdfExportQuality.HIGH,
                            onClick = { viewModel.setQuality(PdfExportQuality.HIGH) },
                            label = { Text(strings.qualityHigh) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showExportSheet = false
                            val safeName = if (uiState.filename.endsWith(".pdf", true)) {
                                uiState.filename
                            } else "${uiState.filename}.pdf"
                            saveAsLauncher.launch(safeName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.saveAs, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = {
                            showExportSheet = false
                            viewModel.generateAndRegisterPdf { doc ->
                                onOpenGeneratedPdf(doc.id)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.generateAndOpen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun ImagePageRowCard(
    index: Int,
    total: Int,
    page: ImagePageSpec,
    isDragging: Boolean,
    dragTranslationY: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRotate: () -> Unit,
    onCycleFilter: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val filterLabel = when (page.filter) {
        ImageEnhancementFilter.ORIGINAL -> strings.filterOriginal
        ImageEnhancementFilter.BW_SCAN -> strings.filterBwScan
        ImageEnhancementFilter.HIGH_CONTRAST -> strings.filterHighContrast
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 4f else 0f)
            .graphicsLayer {
                translationY = dragTranslationY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .border(
                width = if (isDragging) 1.5.dp else 1.dp,
                color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium
            )
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
            )

            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 88.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = page.uri,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Crop,
                    colorFilter = imageEnhancementColorFilter(page.filter),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = page.rotationDegrees.toFloat() }
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (page.rotationDegrees != 0) {
                        Text(
                            text = "${page.rotationDegrees}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilterChip(
                    selected = page.filter != ImageEnhancementFilter.ORIGINAL,
                    onClick = onCycleFilter,
                    label = { Text(filterLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = strings.moveUp)
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < total - 1,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = strings.moveDown)
                    }
                }
                Row {
                    IconButton(
                        onClick = onRotate,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = strings.rotate90)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = strings.delete,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
