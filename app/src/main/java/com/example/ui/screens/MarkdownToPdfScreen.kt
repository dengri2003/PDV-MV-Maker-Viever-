package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.prefs.PdfPageSize
import com.example.i18n.LocalStrings
import com.example.pdf.MarkdownBlock
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.MarkdownToPdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownToPdfScreen(
    viewModel: MarkdownToPdfViewModel,
    onBack: () -> Unit,
    onOpenGeneratedPdf: (String) -> Unit
) {
    val strings = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(strings.sampleMarkdownTitle, strings.sampleMarkdownBody) {
        viewModel.initSampleIfEmpty(strings.sampleMarkdownTitle, strings.sampleMarkdownBody)
    }

    LaunchedEffect(uiState.errorMessage) {
        val err = uiState.errorMessage
        if (err != null) {
            val text = if (err == "EMPTY") strings.emptyMarkdownError else err
            snackbarHostState.showSnackbar(text)
            viewModel.clearError()
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.exportPdf(externalSaveUri = targetUri) { doc ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenGeneratedPdf(doc.id)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(strings.markdownTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
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
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.includePageNumbers,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = uiState.includePageNumbers,
                                onCheckedChange = { viewModel.togglePageNumbers() }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val base = uiState.title.trim().ifBlank { "Notes" }
                                saveAsLauncher.launch(if (base.endsWith(".pdf", true)) base else "$base.pdf")
                            },
                            enabled = !uiState.isGenerating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.saveAs, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = {
                                viewModel.exportPdf { doc ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenGeneratedPdf(doc.id)
                                }
                            },
                            enabled = !uiState.isGenerating,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("markdown_export_button")
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.generateAndOpen, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !uiState.isPreviewMode,
                        onClick = { viewModel.setPreviewMode(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(strings.editMode) }
                    SegmentedButton(
                        selected = uiState.isPreviewMode,
                        onClick = { viewModel.setPreviewMode(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(strings.previewMode) }
                }

                if (!uiState.isPreviewMode) {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text(strings.documentTitleHint) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("markdown_title_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val snippets = listOf(
                            "H1" to "# ",
                            "H2" to "## ",
                            "•" to "- ",
                            "1." to "1. ",
                            "B" to "**bold**",
                            "I" to "*italic*",
                            "` `" to "`code`",
                            "Quote" to "> ",
                            "---" to "---"
                        )
                        snippets.forEach { (label, snippet) ->
                            AssistChip(
                                onClick = { viewModel.insertSnippet(snippet) },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.markdownBody,
                        onValueChange = viewModel::updateBody,
                        placeholder = { Text(strings.markdownBodyHint) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("markdown_body_input"),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                    )
                } else {
                    // Live Paper Preview
                    Surface(
                        color = Color.White,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (uiState.title.isNotBlank()) {
                                Text(
                                    text = uiState.title,
                                    color = Color(0xFF121216),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(2.5.dp)
                                        .background(Color(0xFFFF4D3D))
                                )
                                HorizontalDivider(color = Color(0xFFE4E0D8))
                            }

                            uiState.parsedBlocks.forEach { block ->
                                when (block) {
                                    is MarkdownBlock.Heading -> {
                                        Text(
                                            text = renderInlinePreview(block.text),
                                            color = Color(0xFF121216),
                                            fontSize = when (block.level) {
                                                1 -> 18.sp
                                                2 -> 15.sp
                                                else -> 13.5.sp
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    is MarkdownBlock.Paragraph -> {
                                        Text(
                                            text = renderInlinePreview(block.text),
                                            color = Color(0xFF242429),
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        )
                                    }
                                    is MarkdownBlock.BulletItem -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("•", color = Color(0xFF121216), fontSize = 13.sp)
                                            Text(
                                                text = renderInlinePreview(block.text),
                                                color = Color(0xFF242429),
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp
                                            )
                                        }
                                    }
                                    is MarkdownBlock.NumberedItem -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("${block.number}.", color = Color(0xFF121216), fontSize = 13.sp)
                                            Text(
                                                text = renderInlinePreview(block.text),
                                                color = Color(0xFF242429),
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp
                                            )
                                        }
                                    }
                                    is MarkdownBlock.Quote -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(22.dp)
                                                    .background(Color(0xFFFF4D3D))
                                            )
                                            Text(
                                                text = renderInlinePreview(block.text),
                                                color = Color(0xFF4B4B55),
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    is MarkdownBlock.CodeBlock -> {
                                        Surface(
                                            color = Color(0xFFF4F2EC),
                                            shape = MaterialTheme.shapes.extraSmall,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = block.code,
                                                color = Color(0xFF1C1C22),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                    MarkdownBlock.Divider -> {
                                        HorizontalDivider(color = Color(0xFFE0DDD5))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun renderInlinePreview(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFFEFECE4)
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
