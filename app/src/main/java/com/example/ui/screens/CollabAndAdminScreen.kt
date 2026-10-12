package com.example.ui.screens

import android.content.Intent
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PaymentRequest
import com.example.data.model.SchoolPortal
import com.example.data.model.SharedNotebook
import com.example.data.model.UltronAdmins
import com.example.data.model.UltronMonthlyUpdates
import com.example.data.model.UserProfile
import com.example.ui.UltronViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollabNotebooksAndPortalsScreen(
    viewModel: UltronViewModel,
    profile: UserProfile,
    notebooks: List<SharedNotebook>,
    portals: List<SchoolPortal>,
    tts: TextToSpeech?
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // 1-Click Notebook Creator State
    var nbTitle by remember { mutableStateOf("") }
    var nbSubject by remember { mutableStateOf("Computer Science") }
    var nbSourceType by remember { mutableStateOf("WEBSITE") }
    var nbSourceUrl by remember { mutableStateOf("") }
    var nbRawNotes by remember { mutableStateOf("") }

    // School Portal Creator State (RBAC: ADMIN, TEACHER, PRINCIPAL)
    var portalSchoolName by remember(profile.schoolName) { mutableStateOf(profile.schoolName) }
    var portalGroupName by remember { mutableStateOf("") }
    var portalDesc by remember { mutableStateOf("") }
    var portalAnnouncement by remember { mutableStateOf("") }
    var portalAcademicYear by remember { mutableStateOf("2026-2027") }
    var portalClassesInfo by remember { mutableStateOf("Class 6 to Class 12") }
    var portalSubjectsInfo by remember { mutableStateOf("Mathematics • Physics • Chemistry • Biology • Computer Science") }
    var portalTimetable by remember { mutableStateOf("Mon-Fri: 09:00 AM - 03:30 PM | Doubt Session: 04:00 PM") }
    var portalHomework by remember { mutableStateOf("Weekly Chapter Practice & Lab Report") }
    var portalCategory by remember { mutableStateOf("CLASS_GROUP") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val canCreateSchoolPortal = profile.isAdminUser || profile.role in listOf("ADMIN", "TEACHER", "PRINCIPAL")
    val groupCategories = listOf("CLASS_GROUP", "SUBJECT_GROUP", "PROJECT_GROUP", "EXAM_PREP", "OFFICIAL_ANNOUNCEMENTS")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("1-Click Collaborative Notebooks") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("School Portals & Voice Groups") }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedTab == 0) {
                // 1-Click Notebook Builder
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1-Click Smart Notebook Creator", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Instantly convert any website link, lecture video URL, app snippet, or study notes into a structured collaborative notebook with peer feedback & workspace chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("WEBSITE", "VIDEO", "SCIENCE_LAB", "TEXT").forEach { src ->
                                FilterChip(
                                    selected = nbSourceType == src,
                                    onClick = { nbSourceType = src },
                                    label = { Text(src) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = nbTitle,
                                onValueChange = { nbTitle = it },
                                label = { Text("Notebook Title") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = nbSubject,
                                onValueChange = { nbSubject = it },
                                label = { Text("Subject") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = nbSourceUrl,
                            onValueChange = { nbSourceUrl = it },
                            label = { Text("Website / Video / App Source URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = nbRawNotes,
                            onValueChange = { nbRawNotes = it },
                            label = { Text("Key Points or Study Prompt") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )

                        Button(
                            onClick = {
                                viewModel.createOneClickNotebook(
                                    title = nbTitle.ifBlank { "Shared $nbSubject Notebook" },
                                    subject = nbSubject,
                                    sourceType = nbSourceType,
                                    sourceUrl = nbSourceUrl,
                                    rawNotesOrPrompt = nbRawNotes,
                                    publishToCommunity = true
                                )
                                nbTitle = ""
                                nbRawNotes = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_notebook_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1-Click Generate & Publish Collaborative Notebook")
                        }
                    }
                }

                // Shared Collaborative Notebooks Feed
                Text("Shared Student Workspaces (${notebooks.size})", style = MaterialTheme.typography.titleLarge)
                notebooks.forEach { nb ->
                    var chatInput by remember(nb.id) { mutableStateOf("") }
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nb.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "By ${nb.authorName} • ${nb.subject} • Source: ${nb.sourceType}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.postNotebookCommentOrLike(nb, "", true)
                                        }
                                    ) {
                                        Icon(Icons.Default.ThumbUp, contentDescription = "Like", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${nb.likesCount}")
                                    }
                                    IconButton(
                                        onClick = {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, nb.title)
                                                putExtra(Intent.EXTRA_TEXT, "${nb.title}\n\n${nb.content}")
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Export Notebook to Cloud"))
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Export Notebook")
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = nb.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            if (nb.feedbackLog.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Peer Feedback & Workspace Chat:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                        Text(nb.feedbackLog, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = chatInput,
                                    onValueChange = { chatInput = it },
                                    placeholder = {
                                        Text(
                                            text = "Add peer review or workspace chat...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = {
                                        if (chatInput.isNotBlank()) {
                                            viewModel.postNotebookCommentOrLike(nb, chatInput, false)
                                            chatInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Feedback")
                                }
                            }
                        }
                    }
                }
            } else {
                // School Portals & Educator Group Workspaces (RBAC Enforced: Only Admins, Teachers & Principals can create)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("School Portal Creation (RBAC: Admins, Teachers & Principals Only)", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        // Role Switcher & RBAC Status Bar
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Active Portal Role: ${if (profile.isAdminUser) "ADMIN (Official)" else profile.role}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!profile.isAdminUser) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Switch Role:", style = MaterialTheme.typography.bodyMedium)
                                        listOf("STUDENT", "TEACHER", "PRINCIPAL").forEach { r ->
                                            FilterChip(
                                                selected = profile.role == r,
                                                onClick = { viewModel.switchUserRole(r) },
                                                label = { Text(r) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!canCreateSchoolPortal) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔒 Role-Based Access Control (RBAC): Your current role is STUDENT. School Portals can ONLY be created by Admins, Teachers, and Principals. As a Student, you can join any School Portal below and participate in the Group Chat with your Teachers, Classmates, and Principal!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Text("Group / Channel Category:", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                groupCategories.forEach { cat ->
                                    FilterChip(
                                        selected = portalCategory == cat,
                                        onClick = { portalCategory = cat },
                                        label = { Text(cat.replace("_", " ")) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = portalSchoolName,
                                    onValueChange = { portalSchoolName = it },
                                    label = { Text("School / Institution Name") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = portalAcademicYear,
                                    onValueChange = { portalAcademicYear = it },
                                    label = { Text("Academic Year") },
                                    modifier = Modifier.width(130.dp),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = portalGroupName,
                                onValueChange = { portalGroupName = it },
                                label = { Text("Study Group / Classroom Portal Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = portalClassesInfo,
                                    onValueChange = { portalClassesInfo = it },
                                    label = { Text("Classes Covered") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = portalSubjectsInfo,
                                    onValueChange = { portalSubjectsInfo = it },
                                    label = { Text("Subjects") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = portalDesc,
                                onValueChange = { portalDesc = it },
                                label = { Text("Portal Description & Syllabus Scope") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = portalTimetable,
                                    onValueChange = { portalTimetable = it },
                                    label = { Text("Class Timetable") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = portalHomework,
                                    onValueChange = { portalHomework = it },
                                    label = { Text("Homework & Assignments") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = portalAnnouncement,
                                onValueChange = { portalAnnouncement = it },
                                label = { Text("Pinned Official Announcement / Exam Deadline") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.createSchoolPortalGroup(
                                            schoolName = portalSchoolName.ifBlank { "Ultron Academy" },
                                            groupName = portalGroupName.ifBlank { "Class 10-A STEM & Exam Group" },
                                            description = portalDesc.ifBlank { "Official group portal for Teachers, Students, and Principal collaboration." },
                                            announcement = portalAnnouncement.ifBlank { "Welcome Teachers, Students & Principal! Share doubts, notes, and updates here." },
                                            academicYear = portalAcademicYear,
                                            classesInfo = portalClassesInfo,
                                            subjectsInfo = portalSubjectsInfo,
                                            timetableInfo = portalTimetable,
                                            homeworkInfo = portalHomework,
                                            groupCategory = portalCategory,
                                            requestSuperAdminApproval = false
                                        )
                                        portalGroupName = ""
                                        portalDesc = ""
                                        portalAnnouncement = ""
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("create_school_portal_button")
                                ) {
                                    Icon(Icons.Default.Groups, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create School Portal (${if (profile.isAdminUser) "ADMIN" else profile.role})")
                                }
                                if (profile.role == "PRINCIPAL" && !profile.isAdminUser) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.createSchoolPortalGroup(
                                                schoolName = portalSchoolName.ifBlank { "Ultron Academy" },
                                                groupName = portalGroupName.ifBlank { "Official Principal School Portal" },
                                                description = portalDesc.ifBlank { "Principal-requested official school portal awaiting Super Admin approval." },
                                                announcement = portalAnnouncement.ifBlank { "Official Principal Notice Board" },
                                                academicYear = portalAcademicYear,
                                                classesInfo = portalClassesInfo,
                                                subjectsInfo = portalSubjectsInfo,
                                                timetableInfo = portalTimetable,
                                                homeworkInfo = portalHomework,
                                                groupCategory = portalCategory,
                                                requestSuperAdminApproval = true
                                            )
                                            portalGroupName = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Submit for Super Admin Approval")
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Filter Bar for Student & Educator Study Groups
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("ALL") + groupCategories).forEach { filterCat ->
                        FilterChip(
                            selected = selectedCategoryFilter == filterCat,
                            onClick = { selectedCategoryFilter = filterCat },
                            label = { Text(filterCat.replace("_", " ")) }
                        )
                    }
                }

                val filteredPortals = remember(portals, selectedCategoryFilter) {
                    if (selectedCategoryFilter == "ALL") portals
                    else portals.filter { it.groupCategory == selectedCategoryFilter }
                }

                Text(
                    text = "Active School Portals — Group Chat (Teachers • Students • Principal) (${filteredPortals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                filteredPortals.forEach { portal ->
                    var groupMsg by remember(portal.id) { mutableStateOf("") }
                    var fileShareName by remember(portal.id) { mutableStateOf("") }

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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${portal.schoolName} (${portal.academicYear}) — ${portal.groupName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Status: ${portal.portalStatus} • Category: ${portal.groupCategory.replace("_", " ")} • Creator: ${portal.creatorName} [${portal.creatorRole}]",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Classes: ${portal.classesInfo} | Subjects: ${portal.subjectsInfo}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val nextVoice = !portal.voiceRoomActive
                                        viewModel.sendPortalGroupMessage(portal, toggleVoice = nextVoice)
                                        if (nextVoice) {
                                            tts?.speak("Joined live study voice room for ${portal.groupName}", TextToSpeech.QUEUE_FLUSH, null, "portal_voice")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (portal.voiceRoomActive) "Voice Live • Leave" else "Join Voice Room")
                                }
                            }

                            // Super Admin Governance Controls (Approve, Reject, Suspend, Revoke)
                            if (profile.isAdminUser) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "🛡️ Super Admin Portal Governance (Current: ${portal.portalStatus})",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    viewModel.adminReviewSchoolPortal(portal, "ACTIVE", "Approved by Super Admin")
                                                }
                                            ) {
                                                Text("Approve / Activate")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.adminReviewSchoolPortal(portal, "SUSPENDED", "Suspended by Super Admin")
                                                }
                                            ) {
                                                Text("Suspend")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.adminReviewSchoolPortal(portal, "REJECTED", "Rejected/Revoked by Super Admin")
                                                }
                                            ) {
                                                Text("Reject / Revoke")
                                            }
                                        }
                                    }
                                }
                            }

                            if (portal.announcement.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "📌 Principal / Teacher Notice: ${portal.announcement}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            // Academic Workspace Details: Timetable & Homework/Assignments
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🗓️ Timetable: ${portal.timetableInfo}", style = MaterialTheme.typography.bodyMedium)
                                    Text("📝 Homework & Assignments: ${portal.homeworkInfo}", style = MaterialTheme.typography.bodyMedium)
                                    Text("📂 Shared Classroom Files: ${portal.sharedFilesInfo}", style = MaterialTheme.typography.bodyMedium)
                                    if (portal.moderationLog.isNotBlank()) {
                                        Text("🛡️ Moderation Log: ${portal.moderationLog}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            if (canCreateSchoolPortal && (profile.isAdminUser || portal.creatorId == profile.userId || profile.role in listOf("TEACHER", "PRINCIPAL"))) {
                                var newAnnouncementText by remember(portal.id) { mutableStateOf("") }
                                var newHomeworkText by remember(portal.id) { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newAnnouncementText,
                                        onValueChange = { newAnnouncementText = it },
                                        placeholder = { Text("Update pinned notice (Admin/Educator)...") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (newAnnouncementText.isNotBlank()) {
                                                viewModel.sendPortalGroupMessage(
                                                    portal = portal,
                                                    message = "📌 Updated Notice: $newAnnouncementText",
                                                    updatedAnnouncement = newAnnouncementText
                                                )
                                                newAnnouncementText = ""
                                            }
                                        }
                                    ) {
                                        Text("Pin Notice")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newHomeworkText,
                                        onValueChange = { newHomeworkText = it },
                                        placeholder = { Text("Assign homework or update timetable...") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (newHomeworkText.isNotBlank()) {
                                                viewModel.sendPortalGroupMessage(
                                                    portal = portal,
                                                    message = "📝 New Homework Assigned: $newHomeworkText",
                                                    updatedHomework = newHomeworkText
                                                )
                                                newHomeworkText = ""
                                            }
                                        }
                                    ) {
                                        Text("Post Homework")
                                    }
                                }
                            }

                            // Multi-Role Group Chat Transcript (Teachers, Students & Principal)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "💬 Live Group Chat (Teachers • Students • Principal)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = portal.chatTranscript,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Quick Role-Aware Group Chat & Media Attachment Actions
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "Ask Teacher a Syllabus Doubt",
                                    "Share Revision Summary with Students",
                                    "Principal Exam Schedule Update",
                                    "📊 Share Generated Diagram",
                                    "🎬 Share Educational Video"
                                ).forEach { preset ->
                                    OutlinedButton(onClick = { groupMsg = preset }) {
                                        Text(preset, maxLines = 1)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = fileShareName,
                                    onValueChange = { fileShareName = it },
                                    placeholder = { Text("Attach file name (e.g. Physics_Notes.pdf)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (fileShareName.isNotBlank()) {
                                            viewModel.sendPortalGroupMessage(
                                                portal = portal,
                                                message = "Shared study file: $fileShareName",
                                                sharedFile = fileShareName
                                            )
                                            fileShareName = ""
                                        }
                                    }
                                ) {
                                    Text("Share File")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = groupMsg,
                                    onValueChange = { groupMsg = it },
                                    placeholder = {
                                        Text(
                                            text = "Chat as [${if (profile.isAdminUser) "ADMIN" else profile.role}] ${profile.fullName}...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("portal_group_chat_input_${portal.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = {
                                        if (groupMsg.isNotBlank()) {
                                            viewModel.sendPortalGroupMessage(portal = portal, message = groupMsg)
                                            groupMsg = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("portal_group_chat_send_${portal.id}")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Group Message")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    viewModel: UltronViewModel,
    profile: UserProfile,
    allPayments: List<PaymentRequest>,
    allUsers: List<UserProfile>
) {
    if (!profile.isAdminUser) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Access Denied: Official Ultron Administrator privileges required.", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var paymentFilter by remember { mutableStateOf("PENDING") }
    val allPortals by viewModel.schoolPortals.collectAsStateWithLifecycle()
    val tabs = listOf(
        "Payments (${allPayments.count { it.status == "PENDING" }})",
        "Users & Plans (${allUsers.size})",
        "School Portals (${allPortals.size})",
        "Usage & System Telemetry"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { idx, t ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(t, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Admin Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(32.dp))
                    Column {
                        Text("Official Ultron Admin Control Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Signed in as ${profile.email} • Official UPI: ${UltronAdmins.OFFICIAL_UPI_ID}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PENDING", "APPROVED", "REJECTED", "ALL").forEach { st ->
                            FilterChip(
                                selected = paymentFilter == st,
                                onClick = { paymentFilter = st },
                                label = { Text("$st (${if (st == "ALL") allPayments.size else allPayments.count { it.status == st }})") }
                            )
                        }
                    }

                    val displayedPayments = if (paymentFilter == "ALL") allPayments else allPayments.filter { it.status == paymentFilter }
                    if (displayedPayments.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text(
                                text = "No $paymentFilter payment requests found.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        displayedPayments.forEach { req ->
                            var adminNote by remember(req.id) { mutableStateOf(req.adminNote) }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${req.studentName} (${req.selectedPlan} • ₹${req.amount})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Text(
                                                text = req.status,
                                                style = MaterialTheme.typography.labelLarge,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text("Request ID: ${req.id}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Student Email: ${req.studentEmail}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Submitted UTR / Payment ID: ${req.utr}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text("Notification Status: ${req.notificationStatus}", style = MaterialTheme.typography.bodyMedium)
                                    if (req.processedByAdmin.isNotBlank()) {
                                        Text("Processed By: ${req.processedByAdmin} • Note: ${req.adminNote}", style = MaterialTheme.typography.bodyMedium)
                                    }

                                    if (req.status == "PENDING") {
                                        OutlinedTextField(
                                            value = adminNote,
                                            onValueChange = { adminNote = it },
                                            label = { Text("Optional Admin Verification Note / Rejection Reason") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.adminVerifyPayment(req, true, adminNote)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("admin_approve_${req.id}"),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Approve & Activate")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.adminVerifyPayment(req, false, adminNote)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("admin_reject_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Reject")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val freeCount = allUsers.count { it.effectivePlan == "FREE" }
                    val basicCount = allUsers.count { it.effectivePlan == "BASIC" }
                    val proCount = allUsers.count { it.effectivePlan == "PRO" }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("User & Subscription Breakdown", style = MaterialTheme.typography.titleMedium)
                            Text("Total Users: ${allUsers.size} • Free: $freeCount • Basic: $basicCount • Pro: $proCount", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    allUsers.forEach { u ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${u.fullName} (${u.role})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${u.effectivePlan} • ${u.subscriptionStatus}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("${u.email} • ${u.studentClass} (${u.section}) • ${u.schoolName}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Level ${u.level} (${u.xp} XP) • Streak: ${u.currentStreak}d • Chats: ${u.chatsUsed} • Images: ${u.imagesUsed}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Super Admin School Portal Governance", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Review, approve, reject, suspend, or revoke School Portal requests from Principals and Educators.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    allPortals.forEach { p ->
                        var modReason by remember(p.id) { mutableStateOf("") }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${p.schoolName} — ${p.groupName} (${p.academicYear})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Creator: ${p.creatorName} (${p.creatorRole}) • Status: ${p.portalStatus}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text("Classes: ${p.classesInfo} | Subjects: ${p.subjectsInfo}", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = modReason,
                                    onValueChange = { modReason = it },
                                    label = { Text("Super Admin Governance Note / Reason") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.adminReviewSchoolPortal(p, "ACTIVE", modReason) }) {
                                        Text("Approve")
                                    }
                                    OutlinedButton(onClick = { viewModel.adminReviewSchoolPortal(p, "SUSPENDED", modReason) }) {
                                        Text("Suspend")
                                    }
                                    OutlinedButton(onClick = { viewModel.adminReviewSchoolPortal(p, "REJECTED", modReason) }) {
                                        Text("Reject / Revoke")
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    val totalChats = allUsers.sumOf { it.chatsUsed }
                    val totalImages = allUsers.sumOf { it.imagesUsed }
                    val totalQuizzes = allUsers.sumOf { it.quizzesCompleted }
                    val totalFocus = allUsers.sumOf { it.focusSessions }
                    val totalStudyMins = allUsers.sumOf { it.studyMinutes }
                    val totalXp = allUsers.sumOf { it.xp }
                    val totalBadges = allUsers.sumOf { it.unlockedBadges.size }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Platform-Wide Usage & Achievement Telemetry", style = MaterialTheme.typography.titleMedium)
                            Text("• Total Gemini Study Chats: $totalChats", style = MaterialTheme.typography.bodyLarge)
                            Text("• Total Image / OCR Scans: $totalImages", style = MaterialTheme.typography.bodyLarge)
                            Text("• Total Quizzes Completed: $totalQuizzes", style = MaterialTheme.typography.bodyLarge)
                            Text("• Total Focus Sessions: $totalFocus ($totalStudyMins mins)", style = MaterialTheme.typography.bodyLarge)
                            Text("• Total Platform XP Earned: $totalXp XP", style = MaterialTheme.typography.bodyLarge)
                            Text("• Total Badges Unlocked: $totalBadges", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("App Monthly Updates Registry", style = MaterialTheme.typography.titleMedium)
                            UltronMonthlyUpdates.UPDATES.forEach { up ->
                                Text("${up.version}: ${up.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                up.highlights.forEach { h ->
                                    Text("• $h", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
