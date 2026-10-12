package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EducationalVideoScene
import com.example.data.remote.SpotifyMusicService
import com.example.data.remote.SpotifyStudyTrack
import java.util.Locale

data class ParsedVisualExplanation(
    val title: String,
    val shortExplanation: String,
    val detailedExplanation: String,
    val labelsAndAnnotations: String,
    val stepByStepBreakdown: String,
    val keyTakeaways: String
)

fun parseStructuredVisualExplanation(topic: String, rawText: String): ParsedVisualExplanation {
    val cleanTopic = topic.substringBefore(" [").substringBefore(" (").trim().ifBlank { "Educational Visual Concept" }
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

    val shortExp = rawText.substringAfter("SHORT EXPLANATION:", "")
        .substringBefore("DETAILED EXPLANATION:")
        .substringBefore("LABELS & ANNOTATIONS:")
        .trim()
        .ifBlank {
            lines.firstOrNull { !it.startsWith("#") && !it.startsWith("🎬") && it.length > 20 }
                ?: "Visual representation of $cleanTopic illustrating core components, governing relationships, and academic applications."
        }

    val detailedExp = rawText.substringAfter("DETAILED EXPLANATION:", "")
        .substringBefore("LABELS & ANNOTATIONS:")
        .substringBefore("STEP-BY-STEP BREAKDOWN:")
        .trim()
        .ifBlank {
            lines.drop(1).take(3).joinToString(" ").ifBlank {
                "This visual demonstrates how primary variables, structural elements, and conservation laws interact within $cleanTopic."
            }
        }

    val labelsExp = rawText.substringAfter("LABELS & ANNOTATIONS:", "")
        .substringBefore("STEP-BY-STEP BREAKDOWN:")
        .substringBefore("KEY TAKEAWAYS:")
        .trim()
        .ifBlank {
            "• Primary Node / Axis: Core system input & reference frame\n• Vectors & Connectors: Direction of energy, signal, or process flow\n• Annotated Callouts: Key governing equations and boundary conditions"
        }

    val stepsExp = rawText.substringAfter("STEP-BY-STEP BREAKDOWN:", "")
        .substringBefore("KEY TAKEAWAYS:")
        .trim()
        .ifBlank {
            "1. Identify the labeled input variables and initial state.\n2. Trace the directional arrows through the core mechanism or equation.\n3. Verify the output state, units, and limiting behavior."
        }

    val takeawaysExp = rawText.substringAfter("KEY TAKEAWAYS:", "")
        .trim()
        .ifBlank {
            "• Always label axes, arrows, and key structures clearly in exams.\n• Connect the visual geometry directly to the governing formula for full marks."
        }

    return ParsedVisualExplanation(
        title = cleanTopic,
        shortExplanation = shortExp,
        detailedExplanation = detailedExp,
        labelsAndAnnotations = labelsExp,
        stepByStepBreakdown = stepsExp,
        keyTakeaways = takeawaysExp
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructuredVisualExplanationCard(
    topic: String,
    rawExplanation: String,
    onAskFollowUp: (String) -> Unit,
    onRefineVisual: (String) -> Unit,
    onOpenFullScreen: () -> Unit,
    tts: TextToSpeech? = null
) {
    val parsed = remember(topic, rawExplanation) {
        parseStructuredVisualExplanation(topic, rawExplanation)
    }
    var followUpInput by remember { mutableStateOf("") }
    var expandedDetails by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📚 Visual Guide: ${parsed.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = parsed.shortExplanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    if (tts != null) {
                        IconButton(
                            onClick = {
                                val speechText = "${parsed.title}. ${parsed.shortExplanation}. ${parsed.detailedExplanation}. Key takeaways: ${parsed.keyTakeaways}"
                                tts.speak(speechText.take(500), TextToSpeech.QUEUE_FLUSH, null, "vis_exp_tts")
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read Visual Explanation")
                        }
                    }
                    IconButton(
                        onClick = onOpenFullScreen,
                        modifier = Modifier.testTag("open_fullscreen_visual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full-Screen Zoom & Inspect",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expandedDetails) "Structured Breakdown & Labels" else "Tap to expand Labels, Steps & Takeaways",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedButton(onClick = { expandedDetails = !expandedDetails }) {
                    Text(if (expandedDetails) "Compact" else "Expand Guide")
                }
            }

            AnimatedVisibility(visible = expandedDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisualSectionBox(
                        heading = "Detailed Concept & Relationships",
                        body = parsed.detailedExplanation
                    )
                    VisualSectionBox(
                        heading = "Labels, Symbols & Annotations",
                        body = parsed.labelsAndAnnotations
                    )
                    VisualSectionBox(
                        heading = "Step-by-Step Process Breakdown",
                        body = parsed.stepByStepBreakdown
                    )
                    VisualSectionBox(
                        heading = "High-Yield Exam Takeaways",
                        body = parsed.keyTakeaways
                    )
                }
            }

            // Quick Visual Refinement & Follow-up Prompts
            Text(
                text = "Refine Visual or Ask a Follow-Up Question:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Simplify Diagram",
                    "Add Detailed Labels",
                    "Textbook Illustration",
                    "3D Visualization",
                    "Step-by-Step Flowchart"
                ).forEach { modifierChip ->
                    FilterChip(
                        selected = false,
                        onClick = { onRefineVisual("${parsed.title} ($modifierChip)") },
                        label = { Text(modifierChip) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = followUpInput,
                    onValueChange = { followUpInput = it },
                    placeholder = {
                        Text(
                            text = "Ask about a label, arrow, or formula...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (followUpInput.isNotBlank()) {
                            onAskFollowUp("Regarding the visual on '${parsed.title}': $followUpInput")
                            followUpInput = ""
                        }
                    }
                ) {
                    Text("Ask")
                }
            }
        }
    }
}

@Composable
private fun VisualSectionBox(heading: String, body: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Full-Screen Interactive Modal Viewer for Diagrams, Images, Charts, and Educational Videos.
 * Supports pinch-to-zoom, pan/drag, double-tap zoom, zoom in/out/reset buttons, explanation drawer toggle,
 * video speed/captions/scrubbing controls, and export/save actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullScreenVisualViewerDialog(
    title: String,
    explanationText: String,
    bitmap: Bitmap? = null,
    videoScenes: List<EducationalVideoScene> = emptyList(),
    tts: TextToSpeech? = null,
    onDismiss: () -> Unit,
    onSaveToLibrary: () -> Unit,
    onRefineVisual: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showExplanationOverlay by remember { mutableStateOf(true) }

    // Video full-screen state
    var activeSceneIdx by remember { mutableIntStateOf(0) }
    var isVideoPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var showCaptions by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val parsed = remember(title, explanationText) {
        parseStructuredVisualExplanation(title, explanationText)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_visual_dialog"),
            color = Color(0xFF040916)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Full-Screen Control Bar
                Surface(
                    color = Color(0xFF0B152C),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = parsed.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (bitmap != null) {
                                    "Full-Screen HD Diagram • Zoom ${(scale * 100).toInt()}% (Pinch or double-tap to zoom)"
                                } else {
                                    "Full-Screen Educational Video • Speed ${playbackSpeed}x"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (bitmap != null) {
                                IconButton(
                                    onClick = { scale = (scale + 0.4f).coerceAtMost(4f) },
                                    modifier = Modifier.testTag("fullscreen_zoom_in_button")
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        scale = (scale - 0.4f).coerceAtLeast(1f)
                                        if (scale == 1f) offset = Offset.Zero
                                    },
                                    modifier = Modifier.testTag("fullscreen_zoom_out_button")
                                ) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        scale = 1f
                                        offset = Offset.Zero
                                    },
                                    modifier = Modifier.testTag("fullscreen_reset_zoom_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom", tint = Color.White)
                                }
                            }

                            IconButton(
                                onClick = { showExplanationOverlay = !showExplanationOverlay },
                                modifier = Modifier.testTag("fullscreen_toggle_explanation_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Toggle Explanation",
                                    tint = if (showExplanationOverlay) Color(0xFF38BDF8) else Color.White
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("fullscreen_close_button")
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Full-Screen", tint = Color.White)
                            }
                        }
                    }
                }

                // Interactive Visual Viewport (Zoomable Image/Diagram OR Full-Screen Video Player)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF050C1D)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.2f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 2.4f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            val maxOffset = 700f * (newScale - 1f)
                                            offset = Offset(
                                                x = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                                y = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                                            )
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Full-Screen Zoomable Diagram",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (videoScenes.isNotEmpty()) {
                        val safeIdx = activeSceneIdx.coerceIn(0, (videoScenes.size - 1).coerceAtLeast(0))
                        val currentScene = videoScenes[safeIdx]

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Main animated simulation in full-screen
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                InlineEducationalVideoPlayer(
                                    topic = parsed.title,
                                    scenes = videoScenes,
                                    tts = if (isMuted) null else tts
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Full-Screen Video Controls (Seek/Scrub, Speed, Mute, Captions, Replay)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0E1B38),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (showCaptions) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xCC020617),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "CC: ${currentScene.narration}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFFE2E8F0),
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Scene ${safeIdx + 1}/${videoScenes.size}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                        Slider(
                                            value = safeIdx.toFloat(),
                                            onValueChange = { newVal ->
                                                activeSceneIdx = newVal.toInt().coerceIn(0, (videoScenes.size - 1).coerceAtLeast(0))
                                            },
                                            valueRange = 0f..(videoScenes.size - 1).coerceAtLeast(1).toFloat(),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                isVideoPlaying = !isVideoPlaying
                                                if (!isVideoPlaying) tts?.stop()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isVideoPlaying) "Pause" else "Play", color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                activeSceneIdx = 0
                                                isVideoPlaying = true
                                                if (!isMuted) {
                                                    videoScenes.firstOrNull()?.let {
                                                        tts?.speak(it.narration, TextToSpeech.QUEUE_FLUSH, null, "fs_replay")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Replay", color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                isMuted = !isMuted
                                                if (isMuted) tts?.stop()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isMuted) "Muted" else "Audio On", color = Color.White)
                                        }

                                        OutlinedButton(onClick = { showCaptions = !showCaptions }) {
                                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (showCaptions) "Captions: ON" else "Captions: OFF", color = Color.White)
                                        }

                                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                                            FilterChip(
                                                selected = playbackSpeed == spd,
                                                onClick = {
                                                    playbackSpeed = spd
                                                    tts?.setSpeechRate(spd)
                                                },
                                                label = { Text("${spd}x") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Collapsible Full-Screen Explanation & Action Drawer
                AnimatedVisibility(visible = showExplanationOverlay) {
                    Surface(
                        color = Color(0xFF0B152C),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📖 Complete Visual Explanation & Labels",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onSaveToLibrary) {
                                        Text("Save to Library", color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "Ultron Study Visual: ${parsed.title}")
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "${parsed.title}\n\n${parsed.shortExplanation}\n\n${parsed.detailedExplanation}\n\nLabels:\n${parsed.labelsAndAnnotations}\n\nKey Takeaways:\n${parsed.keyTakeaways}"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Visual Notes"))
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", color = Color.White)
                                    }
                                }
                            }

                            Text(
                                text = "Overview: ${parsed.shortExplanation}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Detailed Mechanism: ${parsed.detailedExplanation}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = "Labels & Annotations:\n${parsed.labelsAndAnnotations}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7DD3FC)
                            )
                            Text(
                                text = "Step-by-Step Breakdown:\n${parsed.stepByStepBreakdown}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "Key Exam Takeaways:\n${parsed.keyTakeaways}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF34D399)
                            )

                            if (onRefineVisual != null) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Enlarge Labels", "Simplify for Revision", "Add Formula Annotations").forEach { action ->
                                        OutlinedButton(
                                            onClick = {
                                                onRefineVisual("${parsed.title} - $action")
                                                onDismiss()
                                            }
                                        ) {
                                            Text(action, color = Color(0xFF38BDF8))
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
}

/**
 * Full Spotify Music Search, Focus Playlists, and Playback Control Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpotifyStudyMusicCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SpotifyMusicService.initialize(context)
    }
    val state by SpotifyMusicService.playerState.collectAsStateWithLifecycle()
    var searchInput by remember { mutableStateOf(state.searchQuery) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spotify_music_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Official Spotify Branding Accent & Verified Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1DB954),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Spotify Study Music",
                                tint = Color.Black
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Spotify Study & Focus Music",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.connectionStatusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isConnectedToSpotifyApi) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                OutlinedButton(
                    onClick = { SpotifyMusicService.toggleEmbeddedSpotifyPlayer() },
                    modifier = Modifier.testTag("spotify_connect_verify_button")
                ) {
                    Text(if (state.showEmbeddedSpotifyPlayer) "Hide Embed" else "Spotify Player")
                }
            }

            // Smart Pomodoro Auto-Sync Controls (Saved Automatically, Zero Setup Pop-ups)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = state.autoPlayWithPomodoro,
                    onClick = { SpotifyMusicService.setAutoPlayWithPomodoro(!state.autoPlayWithPomodoro) },
                    label = { Text("Auto-Play/Pause with Pomodoro") },
                    modifier = Modifier.testTag("spotify_auto_pomodoro_chip")
                )
                FilterChip(
                    selected = state.pauseOnPomodoroBreak,
                    onClick = { SpotifyMusicService.setPauseOnPomodoroBreak(!state.pauseOnPomodoroBreak) },
                    label = { Text("Pause Music on Break") }
                )
                FilterChip(
                    selected = state.showEmbeddedSpotifyPlayer,
                    onClick = { SpotifyMusicService.toggleEmbeddedSpotifyPlayer() },
                    label = { Text("Embedded Spotify Player") }
                )
            }

            // Inline Official Spotify Embedded Player (open.spotify.com/embed/...)
            val activeEmbedUrl = state.currentTrack?.spotifyEmbedUrl.orEmpty()
            AnimatedVisibility(visible = state.showEmbeddedSpotifyPlayer && activeEmbedUrl.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF121212),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()
                                loadUrl(activeEmbedUrl)
                            }
                        },
                        update = { webView ->
                            if (webView.url != activeEmbedUrl && activeEmbedUrl.isNotBlank()) {
                                webView.loadUrl(activeEmbedUrl)
                            }
                        }
                    )
                }
            }

            // Search Bar for Songs, Artists, Albums, Playlists, and Genres
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("Search songs, artists, albums, playlists, genres...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("spotify_search_input"),
                    singleLine = true
                )
                Button(
                    onClick = { SpotifyMusicService.searchTracks(searchInput) },
                    modifier = Modifier.testTag("spotify_search_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search Spotify")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search")
                }
            }

            // Search Type Filter Chips: All, Songs, Playlists, Albums, Artists
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpotifyMusicService.SEARCH_FILTER_TYPES.forEach { filterType ->
                    FilterChip(
                        selected = state.searchFilterType == filterType,
                        onClick = { SpotifyMusicService.selectSearchFilterType(filterType) },
                        label = { Text(filterType) }
                    )
                }
            }

            if (state.isSearching || state.isVerifyingConnection) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!state.searchError.isNullOrBlank()) {
                Text(
                    text = state.searchError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Music Genre & Preference Chips (Lo-Fi, Classical, Instrumental, Devotional, Motivational, Pop, Rock, etc.)
            Text(
                text = "Music Genres & Study Preferences (Auto-Saved):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpotifyMusicService.FOCUS_CATEGORIES.forEach { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = {
                            SpotifyMusicService.selectCategory(category)
                        },
                        label = { Text(category) }
                    )
                }
            }

            // Active Now Playing Card with Full Playback Controls
            val currentTrack = state.currentTrack
            if (currentTrack != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.isPlaying) {
                                        "NOW PLAYING • ${currentTrack.category.uppercase()} (${currentTrack.itemType})"
                                    } else {
                                        "SELECTED ${currentTrack.itemType} • ${currentTrack.category.uppercase()}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF1DB954),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentTrack.artist} • ${currentTrack.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { SpotifyMusicService.openTrackInSpotify(context, currentTrack) },
                                modifier = Modifier.testTag("open_in_spotify_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Spotify")
                            }
                        }

                        Text(
                            text = state.playbackModeNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Seek Bar + Elapsed / Duration
                        Column {
                            Slider(
                                value = state.currentPositionSec.toFloat(),
                                onValueChange = { SpotifyMusicService.seekTo(it.toInt()) },
                                valueRange = 0f..currentTrack.durationSec.coerceAtLeast(1).toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTimeMmSs(state.currentPositionSec),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = formatTimeMmSs(currentTrack.durationSec),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // Transport Controls: Shuffle, Previous, Play/Pause, Next, Repeat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { SpotifyMusicService.toggleShuffle() }) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (state.shuffleEnabled) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { SpotifyMusicService.skipPrevious() },
                                modifier = Modifier.testTag("spotify_prev_button")
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track")
                            }
                            Button(
                                onClick = { SpotifyMusicService.togglePlayPause() },
                                modifier = Modifier.testTag("spotify_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.isPlaying) "Pause" else "Play")
                            }
                            IconButton(
                                onClick = { SpotifyMusicService.skipNext() },
                                modifier = Modifier.testTag("spotify_next_button")
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Track")
                            }
                            IconButton(onClick = { SpotifyMusicService.toggleRepeat() }) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (state.repeatEnabled) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Volume Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = state.volume,
                                onValueChange = { SpotifyMusicService.setVolume(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(state.volume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Track & Playlist Results
            Text(
                text = "Study & Spotify Catalog (${state.tracks.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tracks.take(8).forEach { track ->
                    val isCurrent = state.currentTrack?.id == track.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { SpotifyMusicService.playTrack(track) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.category} • ${track.itemType} (${formatTimeMmSs(track.durationSec)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (isCurrent) {
                                            SpotifyMusicService.togglePlayPause()
                                        } else {
                                            SpotifyMusicService.playTrack(track)
                                        }
                                    }
                                ) {
                                    Text(if (isCurrent && state.isPlaying) "Pause" else "Play")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Persistent Mini-Player Bar shown across Ultron screens whenever study music is playing,
 * allowing students to pause, skip, or open Spotify without interrupting their study workflow.
 */
@Composable
fun SpotifyMiniPlayerBar(
    onOpenMusicSection: () -> Unit
) {
    val context = LocalContext.current
    val state by SpotifyMusicService.playerState.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    if (!state.isPlaying && state.currentPositionSec == 0) return

    Surface(
        color = Color(0xFF0E1E17),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenMusicSection() }
            .testTag("spotify_mini_player_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${track.title} • ${formatTimeMmSs(state.currentPositionSec)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA7F3D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SpotifyMusicService.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF1DB954)
                    )
                }
                IconButton(onClick = { SpotifyMusicService.skipNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { SpotifyMusicService.openTrackInSpotify(context, track) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in Spotify",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimeMmSs(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
