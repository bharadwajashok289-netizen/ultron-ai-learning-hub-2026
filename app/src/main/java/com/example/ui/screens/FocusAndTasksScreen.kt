package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusSessionRecord
import com.example.data.model.StudyTask
import com.example.data.model.UserProfile
import com.example.ui.UltronViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusModeScreen(
    viewModel: UltronViewModel,
    profile: UserProfile,
    focusSessions: List<FocusSessionRecord>,
    timerSecondsLeft: Int,
    timerRunning: Boolean,
    timerIsBreak: Boolean,
    focusDurationMin: Int,
    breakDurationMin: Int
) {
    val context = LocalContext.current
    val syllabusBitmaps = remember { mutableStateListOf<Bitmap>() }
    var subject by remember { mutableStateOf("Physics") }
    var topic by remember { mutableStateOf("Electromagnetism & Optics") }
    var manualSyllabus by remember { mutableStateOf("") }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp -> if (bmp != null) syllabusBitmaps.add(bmp) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePictureLauncher.launch(null)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        uris.forEach { uri ->
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { d, _, _ -> d.isMutableRequired = true }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bmp != null) syllabusBitmaps.add(bmp)
            } catch (_: Exception) {
            }
        }
    }

    val minutes = timerSecondsLeft / 60
    val seconds = timerSecondsLeft % 60
    val formattedTimer = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val completedTopicsCount = focusSessions.count { it.completed }
    val totalTopicsCount = focusSessions.size.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pomodoro Focus Timer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Text(
                        text = if (timerIsBreak) "BREAK SESSION ($breakDurationMin min)" else "POMODORO FOCUS TIMER ($focusDurationMin min)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = formattedTimer,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Focus Sessions: ${profile.focusSessions} • Total Study Time: ${profile.studyMinutes} mins • +20 XP / session",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Duration Presets
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to 3, 25 to 5, 45 to 10, 60 to 15).forEach { (fMin, bMin) ->
                        FilterChip(
                            selected = focusDurationMin == fMin,
                            onClick = { viewModel.configurePomodoroDurations(fMin, bMin) },
                            label = { Text("${fMin}m / ${bMin}m") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.togglePomodoroTimer() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_focus_timer_button")
                    ) {
                        Icon(
                            imageVector = if (timerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (timerRunning) "Pause" else "Start Focus")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetPomodoroTimer() },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                    OutlinedButton(
                        onClick = { viewModel.completePomodoroFocusSession(focusDurationMin) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("complete_focus_session_button")
                    ) {
                        Text("Log +20 XP")
                    }
                }

                OutlinedButton(
                    onClick = {
                        viewModel.generateFocusMusic("Binaural 432Hz calm academic focus synth", false)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Lyria Focus Background Chime")
                }
            }
        }

        // Spotify Music Search, Focus Playlists & Playback Control
        SpotifyStudyMusicCard()

        // Syllabus Scanner & Exam Preparation Organizer
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exam Syllabus OCR & Revision Organizer", style = MaterialTheme.typography.titleMedium)
                }

                ImageAttachmentStrip(
                    bitmaps = syllabusBitmaps,
                    onCaptureCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickGallery = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onMoveLeft = { idx ->
                        if (idx > 0) {
                            val item = syllabusBitmaps.removeAt(idx)
                            syllabusBitmaps.add(idx - 1, item)
                        }
                    },
                    onMoveRight = { idx ->
                        if (idx < syllabusBitmaps.size - 1) {
                            val item = syllabusBitmaps.removeAt(idx)
                            syllabusBitmaps.add(idx + 1, item)
                        }
                    },
                    onRemove = { idx -> syllabusBitmaps.removeAt(idx) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Unit / Topic") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = manualSyllabus,
                    onValueChange = { manualSyllabus = it },
                    label = { Text("Syllabus Topics or Exam Chapters (optional if scanning photos)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        viewModel.scanSyllabusAndCreateFocusPlan(
                            subject = subject,
                            topic = topic,
                            durationMinutes = focusDurationMin,
                            syllabusImages = syllabusBitmaps.toList(),
                            manualSyllabusText = manualSyllabus
                        )
                        syllabusBitmaps.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_syllabus_button")
                ) {
                    Text("Scan Syllabus, Generate Summary, Revision Notes & Practice Qs")
                }
            }
        }

        // Preparation Progress & Organized Syllabus Topics
        if (focusSessions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Syllabus Preparation Progress ($completedTopicsCount/${focusSessions.size} Topics Mastered)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LinearProgressIndicator(
                        progress = { completedTopicsCount.toFloat() / totalTopicsCount },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            focusSessions.forEach { session ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${session.subject}: ${session.topic}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (session.completed) "Completed (+20 XP)" else "In Preparation • ${session.durationMinutes}m block",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (session.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (!session.completed) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.completePomodoroFocusSession(session.durationMinutes, session)
                                    }
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Complete")
                                }
                            }
                        }
                        Text("Summary: ${session.syllabusSummary}", style = MaterialTheme.typography.bodyMedium)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Revision Notes:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(session.revisionNotes, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Practice Questions:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                Text(session.practiceQuestions, style = MaterialTheme.typography.bodyMedium)
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
fun TasksScreen(
    viewModel: UltronViewModel,
    tasks: List<StudyTask>
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Mathematics") }
    var deadline by remember { mutableStateOf("Tomorrow 6:00 PM") }
    var priority by remember { mutableStateOf("HIGH") }
    var category by remember { mutableStateOf("TODAY") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            "TODAY" -> tasks.filter { !it.completed && it.category == "TODAY" }
            "TOMORROW" -> tasks.filter { !it.completed && it.category == "TOMORROW" }
            "UPCOMING" -> tasks.filter { !it.completed && it.category == "UPCOMING" }
            "COMPLETED" -> tasks.filter { it.completed }
            "OVERDUE" -> tasks.filter { !it.completed && it.deadline.contains("Overdue", ignoreCase = true) }
            else -> tasks
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add New Task Card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Study Task / Tomorrow Goal (+10 XP)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title (e.g. Finish Electrochemistry NCERT Exercises)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = deadline,
                        onValueChange = { deadline = it },
                        label = { Text("Deadline") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Priority:", style = MaterialTheme.typography.labelLarge)
                    listOf("HIGH", "MEDIUM", "LOW").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Schedule:", style = MaterialTheme.typography.labelLarge)
                    listOf("TODAY", "TOMORROW", "UPCOMING").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
                Button(
                    onClick = {
                        viewModel.addTask(title, subject, deadline, priority, category)
                        title = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Task")
                }
            }
        }

        // Filter Chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "TODAY", "TOMORROW", "UPCOMING", "COMPLETED", "OVERDUE").forEach { f ->
                FilterChip(
                    selected = selectedFilter == f,
                    onClick = { selectedFilter = f },
                    label = { Text(f) }
                )
            }
        }

        if (filteredTasks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "No tasks in $selectedFilter. Add a task above to stay ahead of your study schedule!",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            filteredTasks.forEach { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleTaskCompletion(task) },
                            modifier = Modifier.testTag("toggle_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = if (task.completed) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Complete Task",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${task.subject} • ${task.category} • Priority: ${task.priority} • Due: ${task.deadline}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Task")
                        }
                    }
                }
            }
        }
    }
}
