package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatTurn
import com.example.data.model.EducationalVideoScene
import com.example.data.model.FlashcardDeck
import com.example.data.model.FlashcardItem
import com.example.data.model.HomeworkSession
import com.example.data.model.QuizQuestionItem
import com.example.data.model.ReliableEducationalVideoResource
import com.example.data.model.StudyConversation
import com.example.data.model.StudyQuiz
import com.example.data.model.UserProfile
import com.example.data.remote.GeminiService
import com.example.ui.UltronViewModel
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StudySectionScreen(
    viewModel: UltronViewModel,
    profile: UserProfile,
    conversations: List<StudyConversation>,
    activeTurns: List<ChatTurn>,
    flashcardDecks: List<FlashcardDeck>,
    quizzes: List<StudyQuiz>,
    homeworkSessions: List<HomeworkSession>,
    generatedBitmap: Bitmap?,
    generatedMediaInfo: String?,
    isBusy: Boolean,
    initialTab: Int = 0,
    tts: TextToSpeech?
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("Study Chat", "Flashcards", "Quiz", "Homework Assist", "Science & Media Lab")

    // Shared multi-image picker & camera capture state (Camera permission ONLY requested on button click!)
    val context = LocalContext.current
    val selectedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var cameraPermissionMessage by remember { mutableStateOf<String?>(null) }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            selectedBitmaps.add(bmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionMessage = null
            takePicturePreviewLauncher.launch(null)
        } else {
            cameraPermissionMessage = "Camera permission was denied. You can still upload study photos from your Gallery."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        uris.forEach { uri ->
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bmp != null) selectedBitmaps.add(bmp)
            } catch (_: Exception) {
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        if (cameraPermissionMessage != null) {
            Text(
                text = cameraPermissionMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Reusable Image Upload & Reorder Bar
        ImageAttachmentStrip(
            bitmaps = selectedBitmaps,
            onCaptureCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            onPickGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onMoveLeft = { idx ->
                if (idx > 0) {
                    val item = selectedBitmaps.removeAt(idx)
                    selectedBitmaps.add(idx - 1, item)
                }
            },
            onMoveRight = { idx ->
                if (idx < selectedBitmaps.size - 1) {
                    val item = selectedBitmaps.removeAt(idx)
                    selectedBitmaps.add(idx + 1, item)
                }
            },
            onRemove = { idx -> selectedBitmaps.removeAt(idx) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> StudyChatTabContent(
                    viewModel = viewModel,
                    profile = profile,
                    conversations = conversations,
                    activeTurns = activeTurns,
                    attachedBitmaps = selectedBitmaps.toList(),
                    onClearBitmaps = { selectedBitmaps.clear() },
                    tts = tts
                )
                1 -> FlashcardsTabContent(
                    viewModel = viewModel,
                    decks = flashcardDecks,
                    attachedBitmaps = selectedBitmaps.toList(),
                    onClearBitmaps = { selectedBitmaps.clear() },
                    tts = tts
                )
                2 -> QuizTabContent(
                    viewModel = viewModel,
                    quizzes = quizzes,
                    attachedBitmaps = selectedBitmaps.toList(),
                    onClearBitmaps = { selectedBitmaps.clear() }
                )
                3 -> HomeworkAssistTabContent(
                    viewModel = viewModel,
                    sessions = homeworkSessions,
                    attachedBitmaps = selectedBitmaps.toList(),
                    onClearBitmaps = { selectedBitmaps.clear() },
                    tts = tts
                )
                4 -> ScienceAndMediaStudioTabContent(
                    viewModel = viewModel,
                    generatedBitmap = generatedBitmap,
                    generatedMediaInfo = generatedMediaInfo,
                    attachedBitmaps = selectedBitmaps.toList(),
                    tts = tts
                )
            }
        }
    }
}

@Composable
fun ImageAttachmentStrip(
    bitmaps: List<Bitmap>,
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCaptureCamera,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .testTag("camera_capture_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickGallery,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .testTag("gallery_upload_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery (${bitmaps.size})", maxLines = 1)
                }
                Text(
                    text = "OCR & Study Material",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (bitmaps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    bitmaps.forEachIndexed { index, bmp ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(116.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Uploaded page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    IconButton(
                                        onClick = { onMoveLeft(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onRemove(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onMoveRight(index) },
                                        enabled = index < bitmaps.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyChatTabContent(
    viewModel: UltronViewModel,
    profile: UserProfile,
    conversations: List<StudyConversation>,
    activeTurns: List<ChatTurn>,
    attachedBitmaps: List<Bitmap>,
    onClearBitmaps: () -> Unit,
    tts: TextToSpeech?
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var useHighThinking by remember { mutableStateOf(false) }
    var useFastLite by remember { mutableStateOf(false) }
    var useGoogleSearch by remember { mutableStateOf(false) }
    var useGoogleMaps by remember { mutableStateOf(false) }
    var scienceLabMode by remember { mutableStateOf(false) }
    var autoReadAloud by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var fullScreenChatTurn by remember { mutableStateOf<ChatTurn?>(null) }
    val suggestedVisualTopic by viewModel.suggestedVisualTopic.collectAsStateWithLifecycle()

    if (fullScreenChatTurn != null) {
        val targetTurn = fullScreenChatTurn!!
        val fsBitmap = remember(targetTurn.mediaBase64) {
            if (targetTurn.mediaBase64.isNotBlank()) {
                try {
                    val bytes = Base64.decode(targetTurn.mediaBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            } else null
        }
        FullScreenVisualViewerDialog(
            title = targetTurn.mediaPrompt.ifBlank { "Educational Visual Aid" },
            explanationText = targetTurn.text,
            bitmap = fsBitmap,
            videoScenes = targetTurn.videoScenes,
            reliableVideoSources = targetTurn.reliableVideoSources,
            tts = tts,
            onDismiss = { fullScreenChatTurn = null },
            onSaveToLibrary = {
                viewModel.saveGeneratedMediaToStudyLibrary(
                    title = targetTurn.mediaPrompt.ifBlank { "Study Visual" },
                    mediaType = if (targetTurn.videoScenes.isNotEmpty()) "VIDEO" else "DIAGRAM",
                    summaryNotes = targetTurn.text
                )
            },
            onDownloadToDevice = if (fsBitmap != null) {
                {
                    viewModel.downloadVisualBitmapToDevice(
                        context = context,
                        bitmap = fsBitmap,
                        title = targetTurn.mediaPrompt.ifBlank { "Ultron_Study_Visual" }
                    )
                }
            } else null,
            onRefineVisual = { refinedPrompt ->
                if (targetTurn.videoScenes.isNotEmpty()) {
                    viewModel.sendEducationalVideoInChat(prompt = refinedPrompt, attachedImages = attachedBitmaps)
                } else {
                    viewModel.sendStudyDiagramInChat(prompt = refinedPrompt, attachedImages = attachedBitmaps)
                }
            }
        )
    }

    val chatScrollState = rememberScrollState()
    LaunchedEffect(activeTurns.size) {
        if (activeTurns.isNotEmpty()) {
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top Controls & Single-Line Scrollable Mode Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lang: ${profile.language} • Chats Left: ${profile.chatsRemainingDisplay}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showHistory = !showHistory },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History (${conversations.size})", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { viewModel.startNewChat() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", maxLines = 1)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = useHighThinking,
                onClick = {
                    useHighThinking = !useHighThinking
                    if (useHighThinking) useFastLite = false
                },
                label = { Text("High Thinking (Pro 3.1)", maxLines = 1) }
            )
            FilterChip(
                selected = scienceLabMode,
                onClick = { scienceLabMode = !scienceLabMode },
                label = { Text("Science Skills", maxLines = 1) }
            )
            FilterChip(
                selected = useFastLite,
                onClick = {
                    useFastLite = !useFastLite
                    if (useFastLite) useHighThinking = false
                },
                label = { Text("Fast Lite", maxLines = 1) }
            )
            FilterChip(
                selected = useGoogleSearch,
                onClick = { useGoogleSearch = !useGoogleSearch },
                label = { Text("Google Search", maxLines = 1) }
            )
            FilterChip(
                selected = useGoogleMaps,
                onClick = { useGoogleMaps = !useGoogleMaps },
                label = { Text("Google Maps", maxLines = 1) }
            )
            FilterChip(
                selected = autoReadAloud,
                onClick = { autoReadAloud = !autoReadAloud },
                label = { Text("TTS Audio", maxLines = 1) }
            )
        }

        // Conversation Messages Scroll Area (expands to fill remaining screen height above the docked chat box)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(chatScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showHistory && conversations.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Saved Study Conversations", style = MaterialTheme.typography.titleMedium)
                        conversations.take(5).forEach { conv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.loadSavedConversation(conv)
                                        showHistory = false
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conv.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(conv.lastMessagePreview, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { viewModel.deleteConversation(conv.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Chat")
                                }
                            }
                        }
                    }
                }
            }
            if (activeTurns.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ultron Academic Study Chat & Multimodal Studio",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ask any academic question, generate labeled scientific diagrams & study images, create educational concept videos, or attach study material photos for instant explanation in ${profile.language}.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Explain Newton's 2nd Law with a numerical example",
                                "Draw a diagram of Eukaryotic Cell & Organelles",
                                "Generate an educational video on Planetary Orbits & Gravity",
                                "Derive Quadratic Formula step-by-step"
                            ).forEach { sample ->
                                OutlinedButton(onClick = { promptText = sample }) {
                                    Text(sample, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            } else {
                activeTurns.forEach { turn ->
                    val isUser = turn.role == "user"
                    val decodedBitmap = remember(turn.mediaBase64) {
                        if (turn.mediaBase64.isNotBlank()) {
                            try {
                                val bytes = Base64.decode(turn.mediaBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUser) "You" else "Ultron (${turn.modelUsed})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!isUser) {
                                    IconButton(
                                        onClick = {
                                            tts?.speak(turn.text.take(450), TextToSpeech.QUEUE_FLUSH, null, "chat_tts")
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak response")
                                    }
                                }
                            }

                            // Inline Generated Diagram / Study Image inside Study Chat
                            if (decodedBitmap != null) {
                                var inlineZoom by remember { mutableFloatStateOf(1f) }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📊 Generated Study Visual: ${turn.mediaPrompt}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { inlineZoom = (inlineZoom - 0.25f).coerceAtLeast(1f) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { inlineZoom = (inlineZoom + 0.25f).coerceAtMost(2.5f) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                                                }
                                                OutlinedButton(
                                                    onClick = { fullScreenChatTurn = turn },
                                                    modifier = Modifier.testTag("chat_visual_fullscreen_button")
                                                ) {
                                                    Text("Enlarge / Full Screen")
                                                }
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(235.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF060E1F))
                                                .clickable { fullScreenChatTurn = turn },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = decodedBitmap.asImageBitmap(),
                                                contentDescription = "Generated Study Diagram in Chat (Tap to Enlarge)",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(4.dp)
                                                    .graphicsLayer(
                                                        scaleX = inlineZoom,
                                                        scaleY = inlineZoom
                                                    ),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.downloadVisualBitmapToDevice(
                                                        context = context,
                                                        bitmap = decodedBitmap,
                                                        title = turn.mediaPrompt.ifBlank { "Ultron_Study_Visual" }
                                                    )
                                                },
                                                modifier = Modifier.testTag("chat_download_visual_button")
                                            ) {
                                                Text("Download PNG")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.saveGeneratedMediaToStudyLibrary(
                                                        title = turn.mediaPrompt.ifBlank { "Study Diagram" },
                                                        mediaType = "DIAGRAM",
                                                        summaryNotes = turn.text
                                                    )
                                                }
                                            ) {
                                                Text("Save to Library")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.sendStudyDiagramInChat(
                                                        prompt = turn.mediaPrompt.substringBefore(" [").ifBlank { "Scientific Diagram" },
                                                        attachedImages = attachedBitmaps
                                                    )
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Regenerate")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    promptText = turn.mediaPrompt.substringBefore(" [")
                                                }
                                            ) {
                                                Text("Edit Prompt")
                                            }
                                        }
                                        StructuredVisualExplanationCard(
                                            topic = turn.mediaPrompt,
                                            rawExplanation = turn.text,
                                            onAskFollowUp = { followUp ->
                                                viewModel.sendStudyMessage(
                                                    prompt = followUp,
                                                    useHighThinking = useHighThinking,
                                                    useFastLite = useFastLite,
                                                    useGoogleSearch = useGoogleSearch,
                                                    useGoogleMaps = useGoogleMaps,
                                                    scienceLabMode = scienceLabMode
                                                )
                                            },
                                            onRefineVisual = { refinedPrompt ->
                                                viewModel.sendStudyDiagramInChat(
                                                    prompt = refinedPrompt,
                                                    attachedImages = attachedBitmaps
                                                )
                                            },
                                            onOpenFullScreen = { fullScreenChatTurn = turn },
                                            tts = tts
                                        )
                                    }
                                }
                            }

                            // Inline Interactive Educational Video Player inside Study Chat
                            if (turn.videoScenes.isNotEmpty()) {
                                InlineEducationalVideoPlayer(
                                    topic = turn.mediaPrompt.ifBlank { "Educational Concept Lesson" },
                                    scenes = turn.videoScenes,
                                    tts = tts,
                                    reliableSources = turn.reliableVideoSources,
                                    onOpenFullScreen = { fullScreenChatTurn = turn }
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { fullScreenChatTurn = turn },
                                        modifier = Modifier.testTag("chat_video_fullscreen_button")
                                    ) {
                                        Text("Full Screen Video")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.sendEducationalVideoInChat(
                                                prompt = turn.mediaPrompt.substringBefore(" (").ifBlank { "Educational Concept Video" },
                                                attachedImages = attachedBitmaps
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Regenerate Video")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.saveGeneratedMediaToStudyLibrary(
                                                title = turn.mediaPrompt.ifBlank { "Educational Video" },
                                                mediaType = "VIDEO",
                                                summaryNotes = turn.text
                                            )
                                        }
                                    ) {
                                        Text("Save Video to Library")
                                    }
                                }
                                StructuredVisualExplanationCard(
                                    topic = turn.mediaPrompt,
                                    rawExplanation = turn.text,
                                    onAskFollowUp = { followUp ->
                                        viewModel.sendStudyMessage(
                                            prompt = followUp,
                                            useHighThinking = useHighThinking,
                                            useFastLite = useFastLite,
                                            useGoogleSearch = useGoogleSearch,
                                            useGoogleMaps = useGoogleMaps,
                                            scienceLabMode = scienceLabMode
                                        )
                                    },
                                    onRefineVisual = { refinedPrompt ->
                                        viewModel.sendEducationalVideoInChat(
                                            prompt = refinedPrompt,
                                            attachedImages = attachedBitmaps
                                        )
                                    },
                                    onOpenFullScreen = { fullScreenChatTurn = turn },
                                    tts = tts
                                )
                            }

                            if (decodedBitmap == null && turn.videoScenes.isEmpty()) {
                                Text(text = turn.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            // Automatic Educational Visuals Suggestion Banner inside the scrollable area so it never crowds the chat box
            if (!suggestedVisualTopic.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "💡 Automatic Visual Explanation Offer: Would a labeled diagram or educational video help visualize '${suggestedVisualTopic?.take(70)}'?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val topic = suggestedVisualTopic ?: "Scientific Concept"
                                    viewModel.sendStudyDiagramInChat(prompt = topic, attachedImages = attachedBitmaps)
                                }
                            ) {
                                Text("Generate Diagram")
                            }
                            OutlinedButton(
                                onClick = {
                                    val topic = suggestedVisualTopic ?: "Scientific Concept"
                                    viewModel.sendEducationalVideoInChat(prompt = topic, attachedImages = attachedBitmaps)
                                }
                            ) {
                                Text("Generate Video")
                            }
                            OutlinedButton(onClick = { viewModel.dismissSuggestedVisual() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }

        // Docked Chat Composer Box (always fits on screen across portrait, landscape, and IME states)
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick 1-Tap Multimodal Study Chat Generators (AI Image, Labeled Diagram, Educational Video)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val topicToImage = promptText.ifBlank { "Educational Textbook Illustration of Solar System & Planets" }
                            promptText = ""
                            viewModel.sendStudyDiagramInChat(
                                prompt = "$topicToImage (Textbook Illustration)",
                                attachedImages = attachedBitmaps,
                                onSpeakResponse = if (autoReadAloud) {
                                    { ans -> tts?.speak(ans.take(350), TextToSpeech.QUEUE_FLUSH, null, "img_tts") }
                                } else null
                            )
                            onClearBitmaps()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 34.dp)
                            .testTag("chat_generate_image_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Image", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            val topicToDraw = promptText.ifBlank { "Labeled Scientific Process & Concept Diagram" }
                            promptText = ""
                            viewModel.sendStudyDiagramInChat(
                                prompt = topicToDraw,
                                attachedImages = attachedBitmaps,
                                onSpeakResponse = if (autoReadAloud) {
                                    { ans -> tts?.speak(ans.take(350), TextToSpeech.QUEUE_FLUSH, null, "diag_tts") }
                                } else null
                            )
                            onClearBitmaps()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 34.dp)
                            .testTag("chat_generate_diagram_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Diagram / Map", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            val topicToVideo = promptText.ifBlank { "Newton's Laws of Motion & Orbital Mechanics" }
                            promptText = ""
                            viewModel.sendEducationalVideoInChat(
                                prompt = topicToVideo,
                                attachedImages = attachedBitmaps,
                                onSpeakResponse = if (autoReadAloud) {
                                    { ans -> tts?.speak(ans.take(350), TextToSpeech.QUEUE_FLUSH, null, "vid_tts") }
                                } else null
                            )
                            onClearBitmaps()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 34.dp)
                            .testTag("chat_generate_video_button")
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edu Video", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text(
                                text = "Ask a question, diagram, or video topic...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 112.dp)
                            .testTag("study_chat_input"),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            val toSend = promptText
                            promptText = ""
                            viewModel.sendStudyMessage(
                                prompt = toSend,
                                useHighThinking = useHighThinking,
                                useFastLite = useFastLite,
                                useGoogleSearch = useGoogleSearch,
                                useGoogleMaps = useGoogleMaps,
                                scienceLabMode = scienceLabMode,
                                attachedImages = attachedBitmaps,
                                onSpeakResponse = if (autoReadAloud) {
                                    { ans -> tts?.speak(ans.take(400), TextToSpeech.QUEUE_FLUSH, null, "auto_tts") }
                                } else null
                            )
                            onClearBitmaps()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_study_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineEducationalVideoPlayer(
    topic: String,
    scenes: List<EducationalVideoScene>,
    tts: TextToSpeech?,
    reliableSources: List<ReliableEducationalVideoResource> = emptyList(),
    onOpenFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var activeSceneIdx by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var showSubtitles by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var activeReliableWebUrl by remember { mutableStateOf<String?>(null) }

    val resolvedSources = remember(topic, reliableSources) {
        if (reliableSources.isNotEmpty()) reliableSources
        else GeminiService.getReliableEducationalVideoSources(topic)
    }

    val safeIdx = activeSceneIdx.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))
    val currentScene = scenes.getOrNull(safeIdx) ?: return

    val infiniteTransition = rememberInfiniteTransition(label = "edu_video_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (3600 / playbackSpeed).toInt().coerceIn(1200, 6000), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edu_video_phase"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF060E1F),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_educational_video_player")
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
                        text = "🎬 Educational Video Player • ${currentScene.visualType} (${playbackSpeed}x)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = currentScene.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenFullScreen != null) {
                        IconButton(onClick = onOpenFullScreen) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen Video", tint = Color(0xFF38BDF8))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            tts?.setSpeechRate(playbackSpeed)
                            tts?.speak(currentScene.narration, TextToSpeech.QUEUE_FLUSH, null, "edu_scene_tts")
                        }
                    ) {
                        Text("Narrate")
                    }
                    OutlinedButton(
                        onClick = {
                            isPlaying = !isPlaying
                            if (!isPlaying) {
                                tts?.stop()
                            }
                        }
                    ) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    if (scenes.size > 1) {
                        OutlinedButton(
                            onClick = {
                                val nextIdx = (safeIdx + 1) % scenes.size
                                activeSceneIdx = nextIdx
                                scenes.getOrNull(nextIdx)?.let { sc ->
                                    tts?.setSpeechRate(playbackSpeed)
                                    tts?.speak(sc.narration, TextToSpeech.QUEUE_FLUSH, null, "edu_scene_$nextIdx")
                                }
                            }
                        ) {
                            Text("Next")
                        }
                    }
                }
            }

            // Animated 2D & 3D Scientific / Historical / Mathematical Simulation Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A1633))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val animPhase = if (isPlaying) phase * playbackSpeed else 1.2f

                    when (currentScene.visualType) {
                        "WAVE" -> {
                            val path = Path()
                            val path2 = Path()
                            val steps = 90
                            for (i in 0..steps) {
                                val x = (i.toFloat() / steps) * w
                                val y = cy + sin((i.toFloat() / 9f) + animPhase) * (h * 0.28f)
                                val y2 = cy + cos((i.toFloat() / 9f) + animPhase) * (h * 0.18f)
                                if (i == 0) {
                                    path.moveTo(x, y)
                                    path2.moveTo(x, y2)
                                } else {
                                    path.lineTo(x, y)
                                    path2.lineTo(x, y2)
                                }
                            }
                            drawLine(Color(0xFF475569), Offset(0f, cy), Offset(w, cy), strokeWidth = 2f)
                            drawPath(path2, color = Color(0xFFA855F7).copy(alpha = 0.65f), style = Stroke(width = 3.5f))
                            drawPath(path, color = Color(0xFF38BDF8), style = Stroke(width = 5f))
                        }
                        "BIOLOGY" -> {
                            drawCircle(Color(0xFF10B981).copy(alpha = 0.22f), radius = h * 0.36f, center = Offset(cx, cy))
                            drawCircle(Color(0xFF10B981), radius = h * 0.36f, center = Offset(cx, cy), style = Stroke(width = 4f))
                            val nucOffset = Offset(cx + cos(animPhase) * 14f, cy + sin(animPhase) * 10f)
                            drawCircle(Color(0xFF38BDF8), radius = h * 0.14f, center = nucOffset)
                            for (k in 0..4) {
                                val ang = animPhase + k * (2f * PI.toFloat() / 5f)
                                val ox = cx + cos(ang) * (h * 0.25f)
                                val oy = cy + sin(ang) * (h * 0.22f)
                                drawCircle(Color(0xFFF59E0B), radius = 8f, center = Offset(ox, oy))
                            }
                        }
                        "GRAPH" -> {
                            drawLine(Color(0xFF64748B), Offset(w * 0.12f, h * 0.85f), Offset(w * 0.9f, h * 0.85f), strokeWidth = 3f)
                            drawLine(Color(0xFF64748B), Offset(w * 0.12f, h * 0.15f), Offset(w * 0.12f, h * 0.85f), strokeWidth = 3f)
                            val curve = Path()
                            for (i in 0..60) {
                                val t = i / 60f
                                val x = w * 0.12f + t * (w * 0.75f)
                                val y = h * 0.85f - (t * t) * (h * 0.65f)
                                if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
                            }
                            drawPath(curve, color = Color(0xFF10B981), style = Stroke(width = 4.5f))
                            val markerT = ((sin(animPhase) + 1f) / 2f).coerceIn(0.1f, 0.95f)
                            val mx = w * 0.12f + markerT * (w * 0.75f)
                            val my = h * 0.85f - (markerT * markerT) * (h * 0.65f)
                            drawCircle(Color(0xFF38BDF8), radius = 10f, center = Offset(mx, my))
                        }
                        "CIRCUIT" -> {
                            val left = w * 0.18f
                            val right = w * 0.82f
                            val top = h * 0.22f
                            val bottom = h * 0.78f
                            drawLine(Color(0xFF38BDF8), Offset(left, top), Offset(right, top), strokeWidth = 4f)
                            drawLine(Color(0xFF38BDF8), Offset(right, top), Offset(right, bottom), strokeWidth = 4f)
                            drawLine(Color(0xFF38BDF8), Offset(right, bottom), Offset(left, bottom), strokeWidth = 4f)
                            drawLine(Color(0xFF38BDF8), Offset(left, bottom), Offset(left, top), strokeWidth = 4f)
                            val progress = ((animPhase % (2f * PI.toFloat())) / (2f * PI.toFloat())).coerceIn(0f, 1f)
                            val chargeX = left + (right - left) * progress
                            drawCircle(Color(0xFF10B981), radius = 9f, center = Offset(chargeX, top))
                            val chargeBottomX = right - (right - left) * progress
                            drawCircle(Color(0xFFF59E0B), radius = 9f, center = Offset(chargeBottomX, bottom))
                        }
                        "TIMELINE", "HISTORY_TIMELINE" -> {
                            val yLine = cy
                            drawLine(Color(0xFF38BDF8), Offset(w * 0.1f, yLine), Offset(w * 0.9f, yLine), strokeWidth = 5f)
                            val milestoneCount = 4
                            val activeDot = ((animPhase / (2f * PI.toFloat())) * milestoneCount).toInt().coerceIn(0, milestoneCount - 1)
                            for (m in 0 until milestoneCount) {
                                val mx = w * (0.15f + m * 0.23f)
                                drawCircle(
                                    color = if (m == activeDot) Color(0xFFF59E0B) else Color(0xFF10B981),
                                    radius = if (m == activeDot) 15f else 10f,
                                    center = Offset(mx, yLine)
                                )
                            }
                        }
                        "GEOMETRY_3D", "CHEMISTRY_3D", "CHEMISTRY_MOLECULE" -> {
                            // 3D Rotating Isometric Projection
                            val radius = h * 0.28f
                            val pts = (0..3).map { idx ->
                                val a = animPhase + idx * (PI.toFloat() / 2f)
                                Offset(cx + cos(a) * radius, cy + sin(a) * (radius * 0.45f) - 24f)
                            }
                            val apex = Offset(cx, cy - radius * 0.95f)
                            val baseCenter = Offset(cx, cy + radius * 0.65f)
                            pts.forEachIndexed { idx, pt ->
                                val nextPt = pts[(idx + 1) % pts.size]
                                drawLine(Color(0xFF38BDF8), pt, nextPt, strokeWidth = 3.5f)
                                drawLine(Color(0xFF10B981), apex, pt, strokeWidth = 3f)
                                drawLine(Color(0xFFA855F7).copy(alpha = 0.7f), baseCenter, pt, strokeWidth = 2.5f)
                                drawCircle(Color(0xFFF59E0B), radius = 8f, center = pt)
                            }
                            drawCircle(Color(0xFF38BDF8), radius = 11f, center = apex)
                        }
                        "MAP_GEO", "GEOGRAPHY_EARTH" -> {
                            // Latitude / Longitude Globe & Tectonic / Climate Flow
                            val rGlobe = h * 0.36f
                            drawCircle(Color(0xFF0284C7).copy(alpha = 0.25f), radius = rGlobe, center = Offset(cx, cy))
                            drawCircle(Color(0xFF38BDF8), radius = rGlobe, center = Offset(cx, cy), style = Stroke(width = 4f))
                            drawLine(Color(0xFFF59E0B), Offset(cx - rGlobe, cy), Offset(cx + rGlobe, cy), strokeWidth = 3f)
                            drawLine(Color(0xFF10B981), Offset(cx, cy - rGlobe), Offset(cx, cy + rGlobe), strokeWidth = 2.5f)
                            val satX = cx + cos(animPhase) * (rGlobe * 0.75f)
                            val satY = cy + sin(animPhase * 0.6f) * (rGlobe * 0.45f)
                            drawCircle(Color(0xFFF43F5E), radius = 9f, center = Offset(satX, satY))
                        }
                        else -> {
                            // ORBIT / 3D DYNAMICS
                            val r1 = h * 0.22f
                            val r2 = h * 0.36f
                            drawCircle(Color(0xFF38BDF8), radius = 18f, center = Offset(cx, cy))
                            drawCircle(Color(0xFF1E293B), radius = r1, center = Offset(cx, cy), style = Stroke(width = 2.5f))
                            drawCircle(Color(0xFF1E293B), radius = r2, center = Offset(cx, cy), style = Stroke(width = 2.5f))
                            val p1 = Offset(cx + cos(animPhase) * r1, cy + sin(animPhase) * r1)
                            val p2 = Offset(cx + cos(-animPhase * 0.7f) * r2, cy + sin(-animPhase * 0.7f) * r2)
                            drawCircle(Color(0xFF10B981), radius = 10f, center = p1)
                            drawCircle(Color(0xFFF59E0B), radius = 12f, center = p2)
                        }
                    }
                }
                Surface(
                    color = Color(0xCC030712),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = currentScene.formulaOrKeyRule,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (showSubtitles) {
                    Surface(
                        color = Color(0xD9020617),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "CC: ${currentScene.subtitleText.ifBlank { currentScene.narration }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Playback Speed & Subtitles Control Bar
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = showSubtitles,
                    onClick = { showSubtitles = !showSubtitles },
                    label = { Text(if (showSubtitles) "Subtitles: ON" else "Subtitles: OFF") }
                )
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

            Text(
                text = currentScene.narration,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE2E8F0)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scenes.forEachIndexed { idx, sc ->
                    FilterChip(
                        selected = safeIdx == idx,
                        onClick = {
                            activeSceneIdx = idx
                            tts?.setSpeechRate(playbackSpeed)
                            tts?.speak(sc.narration, TextToSpeech.QUEUE_FLUSH, null, "edu_scene_$idx")
                        },
                        label = { Text("Scene ${sc.sceneNumber}") }
                    )
                }
            }

            // Verified Educational Video & Interactive Simulation Sources (Reliable Fallback & Enrichment)
            if (resolvedSources.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E1B38),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🌐 Verified Educational Video & Interactive Sources (PhET / Khan Academy / NASA):",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF38BDF8)
                        )
                        resolvedSources.forEach { src ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${src.title} (${src.provider})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = src.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 2
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            activeReliableWebUrl = if (activeReliableWebUrl == src.url) null else src.url
                                        }
                                    ) {
                                        Text(if (activeReliableWebUrl == src.url) "Hide Player" else "Open In-App")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url)))
                                            }
                                        }
                                    ) {
                                        Text("Browser")
                                    }
                                }
                            }
                        }

                        if (!activeReliableWebUrl.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            webChromeClient = WebChromeClient()
                                            webViewClient = WebViewClient()
                                            loadUrl(activeReliableWebUrl!!)
                                        }
                                    },
                                    update = { webView ->
                                        if (webView.url != activeReliableWebUrl && !activeReliableWebUrl.isNullOrBlank()) {
                                            webView.loadUrl(activeReliableWebUrl!!)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardsTabContent(
    viewModel: UltronViewModel,
    decks: List<FlashcardDeck>,
    attachedBitmaps: List<Bitmap>,
    onClearBitmaps: () -> Unit,
    tts: TextToSpeech?
) {
    var deckName by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Physics") }
    var topicNotes by remember { mutableStateOf("") }
    var manualFront by remember { mutableStateOf("") }
    var manualBack by remember { mutableStateOf("") }
    val manualDraftCards = remember { mutableStateListOf<FlashcardItem>() }
    var activeDeck by remember { mutableStateOf<FlashcardDeck?>(null) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val currentDeck = activeDeck ?: decks.firstOrNull()
        if (currentDeck != null) {
            val cards = remember(currentDeck.cardsPayload) {
                try {
                    GeminiService.json.decodeFromString<List<FlashcardItem>>(currentDeck.cardsPayload)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (cards.isNotEmpty()) {
                val safeIdx = cardIndex.coerceIn(0, cards.size - 1)
                val currentCard = cards[safeIdx]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFlipped = !isFlipped }
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${currentDeck.name} (${currentDeck.subject})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mastered: ${currentDeck.masteredCards}/${currentDeck.totalCards} • Left: ${(currentDeck.totalCards - currentDeck.masteredCards).coerceAtLeast(0)}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        LinearProgressIndicator(
                            progress = { currentDeck.masteredCards.toFloat() / currentDeck.totalCards.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isFlipped) "ANSWER / BACK" else "QUESTION / FRONT (Tap to Flip)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isFlipped) currentCard.back else currentCard.front,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (safeIdx > 0) {
                                        cardIndex = safeIdx - 1
                                        isFlipped = false
                                    }
                                },
                                enabled = safeIdx > 0
                            ) {
                                Text("Prev")
                            }
                            OutlinedButton(onClick = { isFlipped = !isFlipped }) {
                                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flip (${safeIdx + 1}/${cards.size})")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (safeIdx < cards.size - 1) {
                                        cardIndex = safeIdx + 1
                                        isFlipped = false
                                    }
                                },
                                enabled = safeIdx < cards.size - 1
                            ) {
                                Text("Next")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.markFlashcardStatus(currentDeck, currentCard.id, false)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Still Learning")
                            }
                            Button(
                                onClick = {
                                    viewModel.markFlashcardStatus(currentDeck, currentCard.id, true)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mark_flashcard_known_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Known")
                            }
                        }
                    }
                }
            }
        }

        // AI / OCR Flashcard Generator Card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Generate Flashcards via OCR or Topic", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = deckName,
                        onValueChange = { deckName = it },
                        label = { Text("Deck Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = topicNotes,
                    onValueChange = { topicNotes = it },
                    label = { Text("Topic or Paste Study Notes (or attach Camera/Gallery images above)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Button(
                    onClick = {
                        viewModel.createFlashcardsFromAiOrImages(
                            deckName = deckName.ifBlank { "$subject Deck" },
                            subject = subject,
                            topicOrNotes = topicNotes,
                            images = attachedBitmaps
                        )
                        onClearBitmaps()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_flashcards_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Flashcards (${if (attachedBitmaps.isNotEmpty()) "${attachedBitmaps.size} OCR Images" else "AI Topic"})")
                }
            }
        }

        // Manual Flashcard Deck Builder
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Or Create Flashcards Manually", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = manualFront,
                    onValueChange = { manualFront = it },
                    label = { Text("Card Front (Question / Term)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualBack,
                    onValueChange = { manualBack = it },
                    label = { Text("Card Back (Answer / Definition)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (manualFront.isNotBlank() && manualBack.isNotBlank()) {
                                manualDraftCards.add(
                                    FlashcardItem(
                                        id = "m_${UUID.randomUUID().toString().take(6)}",
                                        front = manualFront.trim(),
                                        back = manualBack.trim()
                                    )
                                )
                                manualFront = ""
                                manualBack = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Card (${manualDraftCards.size})")
                    }
                    Button(
                        onClick = {
                            viewModel.createManualFlashcardDeck(
                                deckName = deckName.ifBlank { "Custom $subject Deck" },
                                subject = subject,
                                cards = manualDraftCards.toList()
                            )
                            manualDraftCards.clear()
                        },
                        enabled = manualDraftCards.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Manual Deck")
                    }
                }
            }
        }

        // Saved Decks List
        if (decks.isNotEmpty()) {
            Text("Saved Flashcard Decks (${decks.size})", style = MaterialTheme.typography.titleMedium)
            decks.forEach { d ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activeDeck = d
                            cardIndex = 0
                            isFlipped = false
                        },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${d.subject} • ${d.masteredCards}/${d.totalCards} Mastered • ${(d.totalCards - d.masteredCards).coerceAtLeast(0)} Remaining",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (d.xpAwarded) {
                            Text("🏆 +25 XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizTabContent(
    viewModel: UltronViewModel,
    quizzes: List<StudyQuiz>,
    attachedBitmaps: List<Bitmap>,
    onClearBitmaps: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Medium") }
    var activeQuiz by remember { mutableStateOf<StudyQuiz?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quiz Generator Card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Adaptive MCQ Quiz Generator", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Enter Quiz Topic (or attach study material above)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quiz_topic_input"),
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Difficulty:", style = MaterialTheme.typography.labelLarge)
                    listOf("Easy", "Medium", "Hard").forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { difficulty = diff },
                            label = { Text(diff) }
                        )
                    }
                }
                Button(
                    onClick = {
                        viewModel.generateQuiz(topic, difficulty, attachedBitmaps)
                        onClearBitmaps()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_quiz_button")
                ) {
                    Icon(Icons.Default.Quiz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate $difficulty MCQ Quiz (+25 XP)")
                }
            }
        }

        val currentQuiz = activeQuiz ?: quizzes.firstOrNull()
        if (currentQuiz != null) {
            val parsedQuestions = remember(currentQuiz.id, currentQuiz.questionsPayload) {
                try {
                    GeminiService.json.decodeFromString<List<QuizQuestionItem>>(currentQuiz.questionsPayload)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            var workingQuestions by remember(currentQuiz.id, currentQuiz.questionsPayload) {
                mutableStateOf(parsedQuestions)
            }
            var showExplanations by remember(currentQuiz.id, currentQuiz.completed) {
                mutableStateOf(currentQuiz.completed)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${currentQuiz.topic} (${currentQuiz.difficulty})",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (showExplanations) {
                                Text(
                                    text = "Score: ${currentQuiz.scorePercent}% (${currentQuiz.correctAnswers}/${currentQuiz.totalQuestions} Correct)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (showExplanations) {
                            OutlinedButton(
                                onClick = {
                                    workingQuestions = workingQuestions.map { it.copy(selectedIndex = -1) }
                                    showExplanations = false
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry")
                            }
                        }
                    }

                    workingQuestions.forEachIndexed { qIdx, qItem ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Q${qIdx + 1}. ${qItem.question}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                qItem.options.forEachIndexed { optIdx, optText ->
                                    val isSelected = qItem.selectedIndex == optIdx
                                    val isCorrect = qItem.correctIndex == optIdx
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !showExplanations) {
                                                workingQuestions = workingQuestions.toMutableList().also {
                                                    it[qIdx] = qItem.copy(selectedIndex = optIdx)
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = if (!showExplanations) {
                                                {
                                                    workingQuestions = workingQuestions.toMutableList().also {
                                                        it[qIdx] = qItem.copy(selectedIndex = optIdx)
                                                    }
                                                }
                                            } else null
                                        )
                                        Text(
                                            text = optText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = when {
                                                showExplanations && isCorrect -> MaterialTheme.colorScheme.tertiary
                                                showExplanations && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            fontWeight = if (showExplanations && isCorrect) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                                if (showExplanations) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Explanation: ${qItem.explanation}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!showExplanations && workingQuestions.isNotEmpty()) {
                        Button(
                            onClick = {
                                showExplanations = true
                                viewModel.submitQuizAnswers(currentQuiz, workingQuestions)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_quiz_button")
                        ) {
                            Text("Submit Quiz & View Explanations")
                        }
                    }
                }
            }
        }

        // Previous Quiz Results History
        if (quizzes.size > 1) {
            Text("Previous Quiz History", style = MaterialTheme.typography.titleMedium)
            quizzes.forEach { q ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeQuiz = q },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${q.topic} (${q.difficulty})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (q.completed) "${q.scorePercent}% (${q.correctAnswers}/${q.totalQuestions})" else "In Progress",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkAssistTabContent(
    viewModel: UltronViewModel,
    sessions: List<HomeworkSession>,
    attachedBitmaps: List<Bitmap>,
    onClearBitmaps: () -> Unit,
    tts: TextToSpeech?
) {
    var subject by remember { mutableStateOf("Mathematics") }
    var manualQuestion by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Homework Assist (OCR & Step-by-Step Learning)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Capture or upload your homework question image above, or type the problem below. Ultron explains the underlying concepts and step-by-step method so you genuinely master the topic.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Physics, Calculus, Chemistry)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = manualQuestion,
                    onValueChange = { manualQuestion = it },
                    label = { Text("Homework Question or Specific Doubt") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                Button(
                    onClick = {
                        viewModel.analyzeHomeworkImages(subject, manualQuestion, attachedBitmaps)
                        onClearBitmaps()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("solve_homework_button")
                ) {
                    Icon(Icons.Default.School, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan & Explain Step-by-Step (+15 XP)")
                }
            }
        }

        sessions.forEach { hw ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(hw.subject, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(
                            onClick = {
                                tts?.speak(hw.stepByStepSolution.take(400), TextToSpeech.QUEUE_FLUSH, null, "hw_tts")
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read Solution")
                        }
                    }
                    Text("Question: ${hw.extractedQuestion}", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Step-by-Step Solution", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(hw.stepByStepSolution, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Core Concept Explanation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Text(hw.conceptExplanation, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScienceAndMediaStudioTabContent(
    viewModel: UltronViewModel,
    generatedBitmap: Bitmap?,
    generatedMediaInfo: String?,
    attachedBitmaps: List<Bitmap>,
    tts: TextToSpeech?
) {
    val context = LocalContext.current
    var mediaPrompt by remember { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf("16:9") }
    var selectedImageSize by remember { mutableStateOf("1K") }
    var selectedDiagramLayout by remember { mutableStateOf("Scientific Diagram") }
    var selectedVideoStyle by remember { mutableStateOf("3D Academic Simulation") }
    var selectedVideoDuration by remember { mutableIntStateOf(8) }
    var useProImageModel by remember { mutableStateOf(true) }
    var videoQuestion by remember { mutableStateOf("Summarize key concepts and formulas") }
    var liveVoicePrompt by remember { mutableStateOf("") }
    var showFullScreenStudioAsset by remember { mutableStateOf(false) }
    val mediaJobs by viewModel.mediaJobs.collectAsStateWithLifecycle()
    val generatedVideoScenes by viewModel.generatedVideoScenes.collectAsStateWithLifecycle()

    val aspectRatios = listOf("1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9", "21:9")
    val imageSizes = listOf("1K", "2K", "4K")
    val diagramLayouts = listOf(
        "Scientific Diagram",
        "Textbook Illustration",
        "3D Visualization",
        "Flowchart",
        "Mind Map",
        "Concept Map",
        "Geography Map",
        "Scientific Chart",
        "Circuit Diagram",
        "Biological Anatomy",
        "Chemistry Structure",
        "Math Graph",
        "Historical Timeline"
    )
    val videoStyles = listOf("3D Academic Simulation", "Whiteboard Explainer", "Laboratory Walkthrough")
    val videoDurations = listOf(4, 6, 8, 12)

    if (showFullScreenStudioAsset && (generatedBitmap != null || generatedVideoScenes.isNotEmpty())) {
        FullScreenVisualViewerDialog(
            title = mediaPrompt.ifBlank { "Generated $selectedDiagramLayout" },
            explanationText = generatedMediaInfo ?: "Educational Visual Explanation",
            bitmap = generatedBitmap,
            videoScenes = generatedVideoScenes,
            reliableVideoSources = GeminiService.getReliableEducationalVideoSources(mediaPrompt.ifBlank { selectedDiagramLayout }),
            tts = tts,
            onDismiss = { showFullScreenStudioAsset = false },
            onSaveToLibrary = {
                viewModel.saveGeneratedMediaToStudyLibrary(
                    title = mediaPrompt.ifBlank { "Generated $selectedDiagramLayout" },
                    mediaType = if (generatedVideoScenes.isNotEmpty()) "VIDEO" else "DIAGRAM",
                    summaryNotes = generatedMediaInfo ?: "Visual study aid"
                )
            },
            onDownloadToDevice = if (generatedBitmap != null) {
                {
                    viewModel.downloadVisualBitmapToDevice(
                        context = context,
                        bitmap = generatedBitmap,
                        title = mediaPrompt.ifBlank { selectedDiagramLayout }
                    )
                }
            } else null,
            onRefineVisual = { refinedPrompt ->
                mediaPrompt = refinedPrompt
                viewModel.generateDiagramOrStudyImage(
                    prompt = refinedPrompt,
                    aspectRatio = selectedAspectRatio,
                    imageSize = selectedImageSize,
                    useStudioPro = useProImageModel,
                    referenceBitmap = attachedBitmaps.firstOrNull(),
                    styleOrLayout = selectedDiagramLayout
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Diagram & High-Quality Image Generation + Editing
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Diagram, Visual Aid & Veo 3.1 Video Generator", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = mediaPrompt,
                    onValueChange = { mediaPrompt = it },
                    label = { Text("Describe the scientific diagram, study visual, or video...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Text("Diagram / Visual Layout & Style:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    diagramLayouts.forEach { layout ->
                        FilterChip(
                            selected = selectedDiagramLayout == layout,
                            onClick = { selectedDiagramLayout = layout },
                            label = { Text(layout) }
                        )
                    }
                }
                Text("Aspect Ratio:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    aspectRatios.forEach { ratio ->
                        FilterChip(
                            selected = selectedAspectRatio == ratio,
                            onClick = { selectedAspectRatio = ratio },
                            label = { Text(ratio) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Resolution:", style = MaterialTheme.typography.labelLarge)
                    imageSizes.forEach { sz ->
                        FilterChip(
                            selected = selectedImageSize == sz,
                            onClick = { selectedImageSize = sz },
                            label = { Text(sz) }
                        )
                    }
                    FilterChip(
                        selected = useProImageModel,
                        onClick = { useProImageModel = !useProImageModel },
                        label = { Text(if (useProImageModel) "Pro Image 3" else "Nano Banana 2.1") }
                    )
                }

                Text("Video Style & Duration:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    videoStyles.forEach { vs ->
                        FilterChip(
                            selected = selectedVideoStyle == vs,
                            onClick = { selectedVideoStyle = vs },
                            label = { Text(vs) }
                        )
                    }
                    videoDurations.forEach { dur ->
                        FilterChip(
                            selected = selectedVideoDuration == dur,
                            onClick = { selectedVideoDuration = dur },
                            label = { Text("${dur}s") }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.generateDiagramOrStudyImage(
                                prompt = mediaPrompt.ifBlank { "Labeled diagram of a eukaryotic cell structure" },
                                aspectRatio = selectedAspectRatio,
                                imageSize = selectedImageSize,
                                useStudioPro = useProImageModel,
                                referenceBitmap = attachedBitmaps.firstOrNull(),
                                styleOrLayout = selectedDiagramLayout
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Image")
                    }
                    Button(
                        onClick = {
                            val veoRatio = if (selectedAspectRatio == "9:16") "9:16" else "16:9"
                            viewModel.generateVeoEducationalVideo(
                                prompt = mediaPrompt.ifBlank { "3D animation of planetary orbital mechanics" },
                                aspectRatio = veoRatio,
                                photo = attachedBitmaps.firstOrNull(),
                                style = selectedVideoStyle,
                                durationSeconds = selectedVideoDuration
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Veo 3.1 Video")
                    }
                }
            }
        }

        // Reliable Media Generation Job Queue & Status Tracker
        if (mediaJobs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Media Generation Job Queue (${mediaJobs.size})", style = MaterialTheme.typography.titleMedium)
                        val hasRunning = mediaJobs.any { it.status in listOf("QUEUED", "PROCESSING") }
                        if (hasRunning) {
                            OutlinedButton(onClick = { viewModel.cancelActiveMediaGeneration() }) {
                                Text("Cancel Active Job")
                            }
                        }
                    }
                    mediaJobs.take(4).forEach { job ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${job.mediaType} • ${job.styleOrLayout} (${job.aspectRatio} • ${job.resolutionOrDuration})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${job.status} (${job.progressPercent}%)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("Prompt: ${job.prompt}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (job.errorMessage.isNotBlank()) {
                                    Text("Error: ${job.errorMessage}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                                if (job.status in listOf("FAILED", "CANCELLED")) {
                                    OutlinedButton(onClick = { viewModel.retryMediaJob(job) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry Generation")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Display Generated Diagram / Media & Cloud Export Button
        if (generatedBitmap != null || !generatedMediaInfo.isNullOrBlank() || generatedVideoScenes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generated Study Asset ($selectedDiagramLayout)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (generatedBitmap != null || generatedVideoScenes.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showFullScreenStudioAsset = true },
                                modifier = Modifier.testTag("studio_visual_fullscreen_button")
                            ) {
                                Text("Full Screen & Zoom")
                            }
                        }
                    }
                    if (generatedBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF060E1F))
                                .clickable { showFullScreenStudioAsset = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = generatedBitmap.asImageBitmap(),
                                contentDescription = "Generated Study Diagram (Tap for Full Screen)",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    if (generatedVideoScenes.isNotEmpty()) {
                        InlineEducationalVideoPlayer(
                            topic = mediaPrompt.ifBlank { "Educational Video Lesson" },
                            scenes = generatedVideoScenes,
                            tts = tts,
                            reliableSources = GeminiService.getReliableEducationalVideoSources(mediaPrompt.ifBlank { "Educational Video Lesson" }),
                            onOpenFullScreen = { showFullScreenStudioAsset = true }
                        )
                    }
                    if (!generatedMediaInfo.isNullOrBlank()) {
                        StructuredVisualExplanationCard(
                            topic = mediaPrompt.ifBlank { selectedDiagramLayout },
                            rawExplanation = generatedMediaInfo,
                            onAskFollowUp = { followUp ->
                                viewModel.sendStudyMessage(
                                    prompt = followUp,
                                    useHighThinking = true,
                                    useFastLite = false,
                                    useGoogleSearch = false,
                                    useGoogleMaps = false,
                                    scienceLabMode = true
                                )
                            },
                            onRefineVisual = { refinedPrompt ->
                                mediaPrompt = refinedPrompt
                                viewModel.generateDiagramOrStudyImage(
                                    prompt = refinedPrompt,
                                    aspectRatio = selectedAspectRatio,
                                    imageSize = selectedImageSize,
                                    useStudioPro = useProImageModel,
                                    referenceBitmap = attachedBitmaps.firstOrNull(),
                                    styleOrLayout = selectedDiagramLayout
                                )
                            },
                            onOpenFullScreen = { showFullScreenStudioAsset = true },
                            tts = tts
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (generatedBitmap != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.downloadVisualBitmapToDevice(
                                        context = context,
                                        bitmap = generatedBitmap,
                                        title = mediaPrompt.ifBlank { selectedDiagramLayout }
                                    )
                                },
                                modifier = Modifier.testTag("studio_download_visual_button")
                            ) {
                                Text("Download PNG")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.generateDiagramOrStudyImage(
                                    prompt = mediaPrompt.ifBlank { "Labeled diagram of a eukaryotic cell structure" },
                                    aspectRatio = selectedAspectRatio,
                                    imageSize = selectedImageSize,
                                    useStudioPro = useProImageModel,
                                    referenceBitmap = attachedBitmaps.firstOrNull(),
                                    styleOrLayout = selectedDiagramLayout
                                )
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.saveGeneratedMediaToStudyLibrary(
                                    title = mediaPrompt.ifBlank { "Generated $selectedDiagramLayout" },
                                    mediaType = if (generatedVideoScenes.isNotEmpty()) "VIDEO" else "DIAGRAM",
                                    summaryNotes = generatedMediaInfo ?: "Visual study aid"
                                )
                            }
                        ) {
                            Text("Save to Study Library")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                viewModel.createOneClickNotebook(
                                    title = mediaPrompt.ifBlank { "Generated Visual Study Asset" },
                                    subject = "Science & Media",
                                    sourceType = "IMAGE",
                                    sourceUrl = "ultron://cloud-storage/export",
                                    rawNotesOrPrompt = generatedMediaInfo ?: "Exported diagram",
                                    publishToCommunity = true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export to Cloud")
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Ultron Study Export")
                                    putExtra(Intent.EXTRA_TEXT, generatedMediaInfo ?: mediaPrompt)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export to Drive / Cloud"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share / Drive")
                        }
                    }
                }
            }
        }

        // 2. Spotify Study & Focus Music Integration
        SpotifyStudyMusicCard()

        // 3. Live Interactive Voice Study Conversation (gemini-2.5-flash) & Lyria Focus Music
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Live Voice Study Session (gemini-2.5-flash) & Focus Music", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = liveVoicePrompt,
                    onValueChange = { liveVoicePrompt = it },
                    label = { Text("Speak or type to Live Voice Companion...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.sendLiveVoicePrompt(
                                spokenText = liveVoicePrompt.ifBlank { "Quiz me verbally on Laws of Thermodynamics" },
                                tts = tts
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Voice Reply")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.generateFocusMusic(
                                prompt = "Calm lo-fi ambient piano and alpha-wave synth for deep exam study",
                                fullLength = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lyria Study Music")
                    }
                }
            }
        }

        // 3. Educational Video Understanding (gemini-3.1-pro-preview)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Analyze Lecture Video (gemini-3.1-pro-preview)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = videoQuestion,
                    onValueChange = { videoQuestion = it },
                    label = { Text("Lecture Video URL / Title & Analysis Prompt") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.analyzeEducationalVideo(
                            videoUrlOrTitle = videoQuestion,
                            question = "Extract key timestamps, formulas, and exam revision notes."
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Analyze Video & Extract Notes")
                }
            }
        }
    }
}
