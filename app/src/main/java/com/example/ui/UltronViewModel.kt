package com.example.ui

import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CachedStudyDao
import com.example.data.local.CachedStudyItemEntity
import com.example.data.model.BadgeDefinition
import com.example.data.model.ChatTurn
import com.example.data.model.FlashcardDeck
import com.example.data.model.FlashcardItem
import com.example.data.model.FocusSessionRecord
import com.example.data.model.HomeworkSession
import com.example.data.model.MediaGenerationJob
import com.example.data.model.PaymentRequest
import com.example.data.model.QuizQuestionItem
import com.example.data.model.SchoolPortal
import com.example.data.model.SharedNotebook
import com.example.data.model.StudyConversation
import com.example.data.model.StudyQuiz
import com.example.data.model.StudyTask
import com.example.data.model.UserProfile
import com.example.data.remote.GeminiService
import com.example.data.remote.SpotifyMusicService
import com.example.data.repository.UltronRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class UltronViewModel(
    private val repository: UltronRepository,
    private val localDao: CachedStudyDao,
    connectivityFlow: Flow<Boolean>,
    val currentUserId: String,
    defaultUserName: String,
    defaultUserEmail: String
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val cachedItems: StateFlow<List<CachedStudyItemEntity>> = localDao.observeAllCachedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _newlyUnlockedBadge = MutableStateFlow<BadgeDefinition?>(null)
    val newlyUnlockedBadge: StateFlow<BadgeDefinition?> = _newlyUnlockedBadge.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .onEach { profile ->
            if (profile != null) {
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "profile_$currentUserId",
                        category = "PROFILE",
                        title = "${profile.fullName} (${profile.studentClass} - ${profile.section})",
                        subtitle = "Plan: ${profile.effectivePlan} • Level ${profile.level} • ${profile.xp} XP • Streak: ${profile.currentStreak}d",
                        contentBody = "School: ${profile.schoolName} | Language: ${profile.language}"
                    )
                )
            }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val tasks: StateFlow<List<StudyTask>> = repository.observeTasks(currentUserId)
        .onEach { list ->
            list.take(20).forEach { t ->
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "task_${t.id}",
                        category = "TASK",
                        title = t.title,
                        subtitle = "${t.subject} • ${t.priority} • ${t.category}",
                        contentBody = "Deadline: ${t.deadline} | Completed: ${t.completed}"
                    )
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<StudyConversation>> = repository.observeConversations(currentUserId)
        .onEach { list ->
            list.take(15).forEach { c ->
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "chat_${c.id}",
                        category = "CHAT",
                        title = c.title,
                        subtitle = "${c.language} • ${c.messageCount} messages",
                        contentBody = c.lastMessagePreview
                    )
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val flashcardDecks: StateFlow<List<FlashcardDeck>> = repository.observeFlashcardDecks(currentUserId)
        .onEach { list ->
            list.take(15).forEach { d ->
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "deck_${d.id}",
                        category = "FLASHCARD",
                        title = d.name,
                        subtitle = "${d.subject} • ${d.masteredCards}/${d.totalCards} mastered",
                        contentBody = d.cardsPayload
                    )
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val quizzes: StateFlow<List<StudyQuiz>> = repository.observeQuizzes(currentUserId)
        .onEach { list ->
            list.take(15).forEach { q ->
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "quiz_${q.id}",
                        category = "QUIZ",
                        title = q.topic,
                        subtitle = "${q.difficulty} • Score: ${q.scorePercent}% (${q.correctAnswers}/${q.totalQuestions})",
                        contentBody = q.questionsPayload
                    )
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val focusSessions: StateFlow<List<FocusSessionRecord>> = repository.observeFocusSessions(currentUserId)
        .onEach { list ->
            list.take(15).forEach { f ->
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "focus_${f.id}",
                        category = "FOCUS",
                        title = "${f.subject}: ${f.topic}",
                        subtitle = "${f.durationMinutes} min • Completed: ${f.completed}",
                        contentBody = f.syllabusSummary + "\n\n" + f.revisionNotes
                    )
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val homeworkSessions: StateFlow<List<HomeworkSession>> = repository.observeHomeworkSessions(currentUserId)
        .onEach { list ->
            list.take(15).forEach { hw ->
                localDao.upsertItem(
                    CachedStudyItemEntity(
                        itemKey = "hw_${hw.id}",
                        category = "HOMEWORK",
                        title = "${hw.subject}: ${hw.extractedQuestion.take(60)}",
                        subtitle = "Step-by-Step Solution & Concept Guide",
                        contentBody = hw.stepByStepSolution + "\n\n" + hw.conceptExplanation
                    )
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val paymentRequests: StateFlow<List<PaymentRequest>> = repository.observeUserPaymentRequests(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminAllPayments: StateFlow<List<PaymentRequest>> = repository.observeAllPaymentRequestsForAdmin()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminAllUsers: StateFlow<List<UserProfile>> = repository.observeAllUsersForAdmin()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sharedNotebooks: StateFlow<List<SharedNotebook>> = repository.observeSharedNotebooks()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val schoolPortals: StateFlow<List<SchoolPortal>> = repository.observeSchoolPortals()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Active Study Chat State
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _activeChatTurns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val activeChatTurns: StateFlow<List<ChatTurn>> = _activeChatTurns.asStateFlow()

    // Generated Multimodal Studio State
    private val _generatedDiagramBitmap = MutableStateFlow<Bitmap?>(null)
    val generatedDiagramBitmap: StateFlow<Bitmap?> = _generatedDiagramBitmap.asStateFlow()

    private val _generatedMediaInfo = MutableStateFlow<String?>(null)
    val generatedMediaInfo: StateFlow<String?> = _generatedMediaInfo.asStateFlow()

    private val _generatedVideoScenes = MutableStateFlow<List<com.example.data.model.EducationalVideoScene>>(emptyList())
    val generatedVideoScenes: StateFlow<List<com.example.data.model.EducationalVideoScene>> = _generatedVideoScenes.asStateFlow()

    // Asynchronous Media Generation Job Queue & Automatic Visual Suggestion State
    private val _mediaJobs = MutableStateFlow<List<MediaGenerationJob>>(emptyList())
    val mediaJobs: StateFlow<List<MediaGenerationJob>> = _mediaJobs.asStateFlow()

    private val _suggestedVisualTopic = MutableStateFlow<String?>(null)
    val suggestedVisualTopic: StateFlow<String?> = _suggestedVisualTopic.asStateFlow()

    private var activeMediaGenJob: Job? = null

    // Pomodoro Focus Timer State
    private val _focusDurationMinutes = MutableStateFlow(25)
    val focusDurationMinutes: StateFlow<Int> = _focusDurationMinutes.asStateFlow()

    private val _breakDurationMinutes = MutableStateFlow(5)
    val breakDurationMinutes: StateFlow<Int> = _breakDurationMinutes.asStateFlow()

    private val _timerSecondsLeft = MutableStateFlow(25 * 60)
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft.asStateFlow()

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()

    private val _timerIsBreak = MutableStateFlow(false)
    val timerIsBreak: StateFlow<Boolean> = _timerIsBreak.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getOrCreateProfile(currentUserId, defaultUserName, defaultUserEmail)
        }
    }

    fun clearBanners() {
        _statusBanner.value = null
        _errorBanner.value = null
    }

    fun dismissUnlockedBadge() {
        _newlyUnlockedBadge.value = null
    }

    fun dismissSuggestedVisual() {
        _suggestedVisualTopic.value = null
    }

    private fun enqueueAndUpdateMediaJob(job: MediaGenerationJob) {
        val current = _mediaJobs.value.filterNot { it.jobId == job.jobId }
        _mediaJobs.value = (listOf(job) + current).take(15)
    }

    fun cancelActiveMediaGeneration(jobId: String? = null) {
        activeMediaGenJob?.cancel()
        activeMediaGenJob = null
        _isBusy.value = false
        val targetId = jobId ?: _mediaJobs.value.firstOrNull { it.status in listOf("QUEUED", "PROCESSING") }?.jobId
        if (targetId != null) {
            _mediaJobs.value = _mediaJobs.value.map {
                if (it.jobId == targetId) it.copy(status = "CANCELLED", progressPercent = 0, errorMessage = "Cancelled by user") else it
            }
            _statusBanner.value = "Media generation job ($targetId) cancelled."
        }
    }

    fun retryMediaJob(job: MediaGenerationJob) {
        when (job.mediaType) {
            "VIDEO" -> generateVeoEducationalVideo(
                prompt = job.prompt,
                aspectRatio = job.aspectRatio,
                photo = null,
                style = job.styleOrLayout,
                durationSeconds = job.resolutionOrDuration.filter { it.isDigit() }.toIntOrNull() ?: 8
            )
            else -> generateDiagramOrStudyImage(
                prompt = job.prompt,
                aspectRatio = job.aspectRatio,
                imageSize = job.resolutionOrDuration.ifBlank { "1K" },
                useStudioPro = true,
                referenceBitmap = null,
                styleOrLayout = job.styleOrLayout
            )
        }
    }

    fun saveGeneratedMediaToStudyLibrary(
        title: String,
        mediaType: String,
        summaryNotes: String
    ) {
        viewModelScope.launch {
            localDao.upsertItem(
                CachedStudyItemEntity(
                    itemKey = "media_lib_${System.currentTimeMillis()}",
                    category = "STUDY_LIBRARY_$mediaType",
                    title = title.take(100).ifBlank { "Saved $mediaType Visual Aid" },
                    subtitle = "Saved to Personal Study Library • $mediaType",
                    contentBody = summaryNotes.take(4000)
                )
            )
            _statusBanner.value = "Saved '$title' to your Personal Study Library (accessible offline)!"
        }
    }

    fun downloadVisualBitmapToDevice(
        context: android.content.Context,
        bitmap: Bitmap,
        title: String
    ) {
        viewModelScope.launch {
            val res = GeminiService.saveBitmapToDeviceDownloads(context, bitmap, title)
            res.onSuccess { savedPath ->
                _statusBanner.value = "Downloaded PNG to device: $savedPath"
            }.onFailure { err ->
                _errorBanner.value = "Could not download visual: ${err.localizedMessage}"
            }
        }
    }

    private fun checkAndSurfaceBadges(unlocked: List<BadgeDefinition>) {
        if (unlocked.isNotEmpty()) {
            _newlyUnlockedBadge.value = unlocked.first()
        }
    }

    // --- ONBOARDING & PROFILE ---

    fun saveUserProfile(
        fullName: String,
        studentClass: String,
        section: String,
        schoolName: String,
        dob: String,
        age: Int,
        role: String,
        language: String,
        themePreference: String,
        onboardingCompleted: Boolean = true
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateProfileDetails(
                userId = currentUserId,
                fullName = fullName,
                studentClass = studentClass,
                section = section,
                schoolName = schoolName,
                dob = dob,
                age = age,
                role = role,
                language = language,
                themePreference = themePreference,
                onboardingCompleted = onboardingCompleted
            )
            _isBusy.value = false
            res.onSuccess {
                _statusBanner.value = "Profile saved successfully."
            }.onFailure {
                _errorBanner.value = it.localizedMessage ?: "Failed to update profile."
            }
        }
    }

    // --- GEMINI STUDY CHAT ---

    fun startNewChat() {
        _activeConversationId.value = null
        _activeChatTurns.value = emptyList()
    }

    fun loadSavedConversation(conversation: StudyConversation) {
        _activeConversationId.value = conversation.id
        val parsed = try {
            GeminiService.json.decodeFromString<List<ChatTurn>>(conversation.messagesPayload)
        } catch (_: Exception) {
            emptyList()
        }
        _activeChatTurns.value = parsed
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(currentUserId, conversationId)
            if (_activeConversationId.value == conversationId) {
                startNewChat()
            }
        }
    }

    fun switchUserRole(newRole: String) {
        val profile = userProfile.value ?: return
        val validRole = if (newRole in listOf("STUDENT", "TEACHER", "PRINCIPAL", "ADMIN")) newRole else "STUDENT"
        saveUserProfile(
            fullName = profile.fullName,
            studentClass = profile.studentClass,
            section = profile.section,
            schoolName = profile.schoolName,
            dob = profile.dob,
            age = profile.age,
            role = validRole,
            language = profile.language,
            themePreference = profile.themePreference,
            onboardingCompleted = true
        )
    }

    fun sendStudyMessage(
        prompt: String,
        useHighThinking: Boolean,
        useFastLite: Boolean,
        useGoogleSearch: Boolean,
        useGoogleMaps: Boolean,
        scienceLabMode: Boolean,
        attachedImages: List<Bitmap> = emptyList(),
        onSpeakResponse: ((String) -> Unit)? = null
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() && attachedImages.isEmpty()) return

        if (cleanPrompt.isNotEmpty() && !GeminiService.isSafeAcademicPrompt(cleanPrompt)) {
            _errorBanner.value = "Academic Safety Policy: Prompts containing inappropriate, harmful, or abusive content are blocked."
            return
        }

        val (serviceType, inferredStyle) = GeminiService.selectBestGenerationService(cleanPrompt)

        if (serviceType == "VIDEO") {
            sendEducationalVideoInChat(
                prompt = cleanPrompt,
                attachedImages = attachedImages,
                onSpeakResponse = onSpeakResponse,
                style = inferredStyle.ifBlank { "3D Academic Simulation" }
            )
            return
        }
        if (serviceType == "DIAGRAM" || serviceType == "IMAGE") {
            sendStudyDiagramInChat(
                prompt = cleanPrompt,
                attachedImages = attachedImages,
                onSpeakResponse = onSpeakResponse,
                styleOrLayout = inferredStyle.ifBlank { if (serviceType == "IMAGE") "Textbook Illustration" else "Scientific Diagram" }
            )
            return
        }

        if (GeminiService.shouldOfferAutomaticVisual(cleanPrompt)) {
            _suggestedVisualTopic.value = cleanPrompt.take(120)
        }

        val profile = userProfile.value

        val userTurn = ChatTurn(role = "user", text = cleanPrompt.ifBlank { "[Attached ${attachedImages.size} Study Image(s)]" })
        val historyBefore = _activeChatTurns.value
        _activeChatTurns.value = historyBefore + userTurn

        viewModelScope.launch {
            _isBusy.value = true
            _errorBanner.value = null

            val language = profile?.language ?: "English"
            val studentClass = profile?.studentClass ?: "Class 12"

            val result = GeminiService.sendStudyChatMessage(
                history = historyBefore,
                userPrompt = cleanPrompt.ifBlank { "Explain the concepts and questions in this study image step by step." },
                language = language,
                studentClass = studentClass,
                useHighThinking = useHighThinking,
                useFastLite = useFastLite,
                useGoogleSearch = useGoogleSearch,
                useGoogleMaps = useGoogleMaps,
                scienceLabMode = scienceLabMode,
                attachedImages = attachedImages
            )

            _isBusy.value = false
            result.onSuccess { (answer, modelUsed) ->
                val modelTurn = ChatTurn(role = "model", text = answer, modelUsed = modelUsed)
                persistAndRewardChatTurn(historyBefore, modelTurn, language, attachedImages.size, onSpeakResponse)
            }.onFailure { err ->
                _errorBanner.value = "Study Chat request failed: ${err.localizedMessage ?: "Network error"}. Tap Retry."
            }
        }
    }

    fun sendStudyDiagramInChat(
        prompt: String,
        attachedImages: List<Bitmap> = emptyList(),
        onSpeakResponse: ((String) -> Unit)? = null,
        styleOrLayout: String = "Scientific Diagram",
        aspectRatio: String = "16:9",
        imageSize: String = "1K"
    ) {
        val cleanPrompt = prompt.trim().ifBlank { "Scientific Concept & Process Diagram" }
        if (!GeminiService.isSafeAcademicPrompt(cleanPrompt)) {
            _errorBanner.value = "Academic Safety Policy: Abusive or inappropriate prompts are not allowed."
            return
        }
        val profile = userProfile.value

        _suggestedVisualTopic.value = null
        val jobId = "job_diag_${System.currentTimeMillis() % 100000}"
        enqueueAndUpdateMediaJob(
            MediaGenerationJob(
                jobId = jobId,
                mediaType = "DIAGRAM",
                prompt = cleanPrompt,
                styleOrLayout = styleOrLayout,
                aspectRatio = aspectRatio,
                resolutionOrDuration = imageSize,
                status = "QUEUED",
                progressPercent = 15
            )
        )

        val userTurn = ChatTurn(role = "user", text = "📊 Generate $styleOrLayout ($aspectRatio • $imageSize): $cleanPrompt")
        val historyBefore = _activeChatTurns.value
        _activeChatTurns.value = historyBefore + userTurn

        activeMediaGenJob = viewModelScope.launch {
            _isBusy.value = true
            _errorBanner.value = null
            val language = profile?.language ?: "English"

            enqueueAndUpdateMediaJob(
                MediaGenerationJob(
                    jobId = jobId,
                    mediaType = "DIAGRAM",
                    prompt = cleanPrompt,
                    styleOrLayout = styleOrLayout,
                    aspectRatio = aspectRatio,
                    resolutionOrDuration = imageSize,
                    status = "PROCESSING",
                    progressPercent = 60
                )
            )

            val studentClass = profile?.studentClass ?: "Class 10"
            val contextHistory = historyBefore.takeLast(4).joinToString(" | ") { "${it.role}: ${it.text.take(120)}" }

            val res = GeminiService.generateStudyDiagramOrImage(
                prompt = cleanPrompt,
                aspectRatio = aspectRatio,
                imageSize = imageSize,
                useStudioProModel = true,
                referenceBitmap = attachedImages.firstOrNull(),
                language = language,
                styleOrLayout = styleOrLayout,
                studentClass = studentClass,
                conversationContext = contextHistory
            )
            _isBusy.value = false

            res.onSuccess { (bmp, explanation) ->
                _generatedDiagramBitmap.value = bmp
                _generatedMediaInfo.value = explanation
                val resolvedStyle = GeminiService.inferBestVisualStyleOrLayout("$cleanPrompt $styleOrLayout", styleOrLayout)
                val resolvedMediaType = if (resolvedStyle.contains("Illustration", ignoreCase = true) || resolvedStyle.contains("Scene", ignoreCase = true) || resolvedStyle.contains("Map", ignoreCase = true)) "IMAGE" else "DIAGRAM"
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = resolvedMediaType,
                        prompt = cleanPrompt,
                        styleOrLayout = resolvedStyle,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = imageSize,
                        status = "COMPLETED",
                        progressPercent = 100,
                        resultSummary = explanation.take(240)
                    )
                )
                val encodedBmp = GeminiService.bitmapToBase64(bmp, 62)
                val modelTurn = ChatTurn(
                    role = "model",
                    text = explanation,
                    modelUsed = GeminiService.MODEL_PRO_IMAGE,
                    mediaType = resolvedMediaType,
                    mediaPrompt = "$cleanPrompt [$resolvedStyle]",
                    mediaBase64 = encodedBmp
                )
                persistAndRewardChatTurn(historyBefore, modelTurn, language, attachedImages.size, onSpeakResponse)
                _statusBanner.value = "Generated $resolvedStyle ($studentClass) in Study Chat!"
            }.onFailure { err ->
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "DIAGRAM",
                        prompt = cleanPrompt,
                        styleOrLayout = styleOrLayout,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = imageSize,
                        status = "FAILED",
                        progressPercent = 0,
                        errorMessage = err.localizedMessage ?: "Generation failed"
                    )
                )
                _errorBanner.value = "Diagram generation failed: ${err.localizedMessage}"
            }
        }
    }

    fun sendEducationalVideoInChat(
        prompt: String,
        attachedImages: List<Bitmap> = emptyList(),
        onSpeakResponse: ((String) -> Unit)? = null,
        style: String = "3D Academic Simulation",
        durationSeconds: Int = 8,
        aspectRatio: String = "16:9"
    ) {
        val cleanPrompt = prompt.trim().ifBlank { "Newton's Laws of Motion & Conservation of Momentum" }
        if (!GeminiService.isSafeAcademicPrompt(cleanPrompt)) {
            _errorBanner.value = "Academic Safety Policy: Abusive or inappropriate prompts are blocked."
            return
        }
        if (!GeminiService.isEducationalVideoTopic(cleanPrompt)) {
            _errorBanner.value = "Educational Purpose Policy: Videos in Ultron must be related to academic & educational subjects (Science, Math, History, Geography, CS, Literature)."
            return
        }

        val profile = userProfile.value

        _suggestedVisualTopic.value = null
        val jobId = "job_vid_${System.currentTimeMillis() % 100000}"
        enqueueAndUpdateMediaJob(
            MediaGenerationJob(
                jobId = jobId,
                mediaType = "VIDEO",
                prompt = cleanPrompt,
                styleOrLayout = style,
                aspectRatio = aspectRatio,
                resolutionOrDuration = "${durationSeconds}s",
                status = "QUEUED",
                progressPercent = 20
            )
        )

        val userTurn = ChatTurn(role = "user", text = "🎬 Generate Educational Video (${durationSeconds}s • $style): $cleanPrompt")
        val historyBefore = _activeChatTurns.value
        _activeChatTurns.value = historyBefore + userTurn

        activeMediaGenJob = viewModelScope.launch {
            _isBusy.value = true
            _errorBanner.value = null
            val language = profile?.language ?: "English"

            enqueueAndUpdateMediaJob(
                MediaGenerationJob(
                    jobId = jobId,
                    mediaType = "VIDEO",
                    prompt = cleanPrompt,
                    styleOrLayout = style,
                    aspectRatio = aspectRatio,
                    resolutionOrDuration = "${durationSeconds}s",
                    status = "PROCESSING",
                    progressPercent = 65
                )
            )

            val studentClass = profile?.studentClass ?: "Class 10"
            val contextHistory = historyBefore.takeLast(4).joinToString(" | ") { "${it.role}: ${it.text.take(120)}" }

            val res = GeminiService.generateEducationalVideoForStudyChat(
                prompt = cleanPrompt,
                language = language,
                aspectRatio = aspectRatio,
                sourcePhoto = attachedImages.firstOrNull(),
                durationSeconds = durationSeconds,
                videoStyle = style,
                studentClass = studentClass,
                conversationContext = contextHistory
            )
            _isBusy.value = false

            res.onSuccess { (summaryText, scenes) ->
                _generatedMediaInfo.value = summaryText
                _generatedVideoScenes.value = scenes
                val reliableSources = GeminiService.getReliableEducationalVideoSources(cleanPrompt, studentClass)
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "VIDEO",
                        prompt = cleanPrompt,
                        styleOrLayout = style,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = "${durationSeconds}s",
                        status = "COMPLETED",
                        progressPercent = 100,
                        resultSummary = summaryText.take(240)
                    )
                )
                val modelTurn = ChatTurn(
                    role = "model",
                    text = summaryText,
                    modelUsed = GeminiService.MODEL_VEO_HIGH_QUALITY,
                    mediaType = "EDUCATIONAL_VIDEO",
                    mediaPrompt = "$cleanPrompt (${durationSeconds}s • $style)",
                    videoScenes = scenes,
                    reliableVideoSources = reliableSources
                )
                persistAndRewardChatTurn(historyBefore, modelTurn, language, attachedImages.size, onSpeakResponse)
                _statusBanner.value = "Educational video lesson (${durationSeconds}s) & verified video sources generated!"
            }.onFailure { err ->
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "VIDEO",
                        prompt = cleanPrompt,
                        styleOrLayout = style,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = "${durationSeconds}s",
                        status = "FAILED",
                        progressPercent = 0,
                        errorMessage = err.localizedMessage ?: "Video generation failed"
                    )
                )
                _errorBanner.value = err.localizedMessage ?: "Failed to generate educational video."
            }
        }
    }

    private fun encodeTurnsSafelyWithinLimit(turns: List<ChatTurn>, maxChars: Int = 92000): String {
        return try {
            val full = GeminiService.json.encodeToString(turns)
            if (full.length <= maxChars) return full

            // Keep mediaBase64 only on the most recent diagram turn to stay well under Firestore 100KB rule
            val lastDiagramIdx = turns.indexOfLast { it.mediaBase64.isNotBlank() }
            val compacted = turns.mapIndexed { idx, turn ->
                if (idx != lastDiagramIdx && turn.mediaBase64.isNotBlank()) {
                    turn.copy(mediaBase64 = "")
                } else {
                    turn
                }
            }
            val compactedJson = GeminiService.json.encodeToString(compacted)
            if (compactedJson.length <= maxChars) return compactedJson

            // Strip all base64 if still over limit and keep recent turns
            val textOnly = compacted.map { it.copy(mediaBase64 = "") }.takeLast(20)
            val textOnlyJson = GeminiService.json.encodeToString(textOnly)
            if (textOnlyJson.length <= maxChars) textOnlyJson else "[]"
        } catch (_: Exception) {
            "[]"
        }
    }

    private suspend fun persistAndRewardChatTurn(
        historyBefore: List<ChatTurn>,
        modelTurn: ChatTurn,
        language: String,
        imagesCount: Int,
        onSpeakResponse: ((String) -> Unit)?
    ) {
        val updatedTurns = _activeChatTurns.value + modelTurn
        _activeChatTurns.value = updatedTurns

        val payloadJson = encodeTurnsSafelyWithinLimit(updatedTurns)
        val convTitle = updatedTurns.firstOrNull()?.text?.take(48) ?: "Study Session"
        val saveRes = repository.saveConversation(
            userId = currentUserId,
            conversationId = _activeConversationId.value,
            title = convTitle,
            language = language,
            lastPreview = modelTurn.text.take(180),
            messageCount = updatedTurns.size,
            messagesPayload = payloadJson
        )
        saveRes.onSuccess { id -> _activeConversationId.value = id }

        val profile = userProfile.value
        if (profile != null) {
            val badgeRes = repository.recordStudyActivityAndRewards(
                currentProfile = profile,
                xpDelta = 5,
                chatsIncrement = 1,
                imagesIncrement = imagesCount,
                questionsDelta = 1,
                studySessionDelta = if (historyBefore.isEmpty()) 1 else 0
            )
            badgeRes.onSuccess { checkAndSurfaceBadges(it) }
        }
        onSpeakResponse?.invoke(modelTurn.text)
    }

    // --- TASKS (TODAY / TOMORROW / UPCOMING / COMPLETED) ---

    fun addTask(title: String, subject: String, deadline: String, priority: String, category: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val res = repository.createTask(title, subject, deadline, priority, category)
            res.onSuccess {
                _statusBanner.value = "Task added to $category."
            }.onFailure {
                _errorBanner.value = it.localizedMessage ?: "Failed to add task."
            }
        }
    }

    fun toggleTaskCompletion(task: StudyTask) {
        viewModelScope.launch {
            val newCompleted = !task.completed
            val awardXpNow = newCompleted && !task.xpAwarded
            val updated = task.copy(
                completed = newCompleted,
                xpAwarded = task.xpAwarded || awardXpNow
            )
            repository.updateTask(currentUserId, updated).onSuccess {
                val profile = userProfile.value
                if (awardXpNow && profile != null) {
                    val badgeRes = repository.recordStudyActivityAndRewards(
                        currentProfile = profile,
                        xpDelta = 10, // Configurable +10 XP for task completion
                        tasksCompletedDelta = 1
                    )
                    badgeRes.onSuccess { checkAndSurfaceBadges(it) }
                    _statusBanner.value = "Task completed! +10 XP earned."
                }
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId)
        }
    }

    // --- FLASHCARDS (MANUAL + CAMERA / GALLERY OCR) ---

    fun createFlashcardsFromAiOrImages(
        deckName: String,
        subject: String,
        topicOrNotes: String,
        images: List<Bitmap>
    ) {
        if (!isOnline.value) {
            _errorBanner.value = "Offline: AI/OCR Flashcard generation requires an internet connection. You can still create manual decks or review saved decks."
            return
        }
        val profile = userProfile.value

        viewModelScope.launch {
            _isBusy.value = true
            val res = GeminiService.generateFlashcards(
                topicOrOcrText = topicOrNotes.ifBlank { "Extract key concepts and definitions from the uploaded study material." },
                subject = subject,
                language = profile?.language ?: "English",
                images = images
            )
            _isBusy.value = false
            res.onSuccess { cards ->
                val payload = GeminiService.json.encodeToString(cards)
                repository.createFlashcardDeck(
                    userId = currentUserId,
                    name = deckName.ifBlank { "$subject Flashcards" },
                    subject = subject,
                    totalCards = cards.size,
                    cardsPayload = payload
                )
                if (profile != null) {
                    repository.recordStudyActivityAndRewards(
                        currentProfile = profile,
                        imagesIncrement = images.size,
                        studySessionDelta = 1
                    ).onSuccess { checkAndSurfaceBadges(it) }
                }
                _statusBanner.value = "Generated ${cards.size} flashcards for $deckName!"
            }.onFailure {
                _errorBanner.value = "Flashcard generation failed: ${it.localizedMessage}"
            }
        }
    }

    fun createManualFlashcardDeck(deckName: String, subject: String, cards: List<FlashcardItem>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val payload = GeminiService.json.encodeToString(cards)
            repository.createFlashcardDeck(
                userId = currentUserId,
                name = deckName.ifBlank { "$subject Deck" },
                subject = subject,
                totalCards = cards.size,
                cardsPayload = payload
            ).onSuccess {
                _statusBanner.value = "Saved manual flashcard deck ($deckName)."
            }
        }
    }

    fun markFlashcardStatus(deck: FlashcardDeck, cardId: String, known: Boolean) {
        viewModelScope.launch {
            val currentCards = try {
                GeminiService.json.decodeFromString<List<FlashcardItem>>(deck.cardsPayload)
            } catch (_: Exception) {
                emptyList()
            }
            if (currentCards.isEmpty()) return@launch
            val updatedCards = currentCards.map { c ->
                if (c.id == cardId) c.copy(mastered = known) else c
            }
            val newMasteredCount = updatedCards.count { it.mastered }
            val newlyCompletedDeck = newMasteredCount == updatedCards.size && !deck.xpAwarded
            val payload = GeminiService.json.encodeToString(updatedCards)

            repository.updateFlashcardDeckProgress(
                userId = currentUserId,
                deckId = deck.id,
                totalCards = updatedCards.size,
                masteredCards = newMasteredCount,
                cardsPayload = payload,
                xpAwarded = deck.xpAwarded || newlyCompletedDeck
            )

            val profile = userProfile.value
            if (profile != null) {
                val masteredDelta = if (known) 1 else 0
                val xpGain = if (newlyCompletedDeck) 25 else 0
                val badgeRes = repository.recordStudyActivityAndRewards(
                    currentProfile = profile,
                    xpDelta = xpGain,
                    flashcardsReviewedDelta = 1,
                    flashcardsMasteredDelta = masteredDelta
                )
                badgeRes.onSuccess { checkAndSurfaceBadges(it) }
                if (newlyCompletedDeck) {
                    _statusBanner.value = "Deck Mastered! +25 XP earned."
                }
            }
        }
    }

    // --- QUIZ SYSTEM (EASY / MEDIUM / HARD) ---

    fun generateQuiz(topic: String, difficulty: String, images: List<Bitmap> = emptyList()) {
        if (!isOnline.value) {
            _errorBanner.value = "Offline: Generating a new AI quiz requires an internet connection. You can still review or retry saved quizzes."
            return
        }
        val profile = userProfile.value

        viewModelScope.launch {
            _isBusy.value = true
            val res = GeminiService.generateMcqQuiz(
                topic = topic.ifBlank { "General Science & Mathematics" },
                difficulty = difficulty,
                language = profile?.language ?: "English",
                images = images
            )
            _isBusy.value = false
            res.onSuccess { questions ->
                val payload = GeminiService.json.encodeToString(questions)
                repository.createQuiz(
                    userId = currentUserId,
                    topic = topic.ifBlank { "Uploaded Material Quiz" },
                    difficulty = difficulty,
                    totalQuestions = questions.size,
                    questionsPayload = payload
                )
                if (profile != null && images.isNotEmpty()) {
                    repository.recordStudyActivityAndRewards(
                        currentProfile = profile,
                        imagesIncrement = images.size
                    )
                }
                _statusBanner.value = "Generated ${questions.size}-question $difficulty Quiz!"
            }.onFailure {
                _errorBanner.value = "Quiz generation failed: ${it.localizedMessage}"
            }
        }
    }

    fun submitQuizAnswers(quiz: StudyQuiz, answeredQuestions: List<QuizQuestionItem>) {
        viewModelScope.launch {
            val total = answeredQuestions.size.coerceAtLeast(1)
            val correct = answeredQuestions.count { it.selectedIndex == it.correctIndex }
            val percent = ((correct * 100f) / total).toInt().coerceIn(0, 100)
            val awardXpNow = !quiz.xpAwarded
            val payload = GeminiService.json.encodeToString(answeredQuestions)

            repository.submitQuizResult(
                userId = currentUserId,
                quizId = quiz.id,
                correctAnswers = correct,
                scorePercent = percent,
                questionsPayload = payload,
                xpAwarded = true
            ).onSuccess {
                val profile = userProfile.value
                if (profile != null) {
                    val xpGain = if (awardXpNow) 25 else 0
                    val badgeRes = repository.recordStudyActivityAndRewards(
                        currentProfile = profile,
                        xpDelta = xpGain,
                        questionsDelta = total,
                        quizzesDelta = if (awardXpNow) 1 else 0,
                        latestQuizScore = percent,
                        studySessionDelta = 1
                    )
                    badgeRes.onSuccess { checkAndSurfaceBadges(it) }
                    _statusBanner.value = "Quiz submitted! Score: $percent% ($correct/$total)${if (awardXpNow) " • +25 XP!" else ""}"
                }
            }
        }
    }

    // --- HOMEWORK ASSIST ---

    fun analyzeHomeworkImages(subject: String, extraQuestion: String, images: List<Bitmap>) {
        if (!isOnline.value) {
            _errorBanner.value = "Offline: Homework OCR and step-by-step analysis require an internet connection."
            return
        }
        val profile = userProfile.value

        viewModelScope.launch {
            _isBusy.value = true
            val instruction = """
                You are Ultron Homework Assist for subject "$subject".
                1. Read the question carefully using OCR from the uploaded image(s) (and note: "$extraQuestion").
                2. First write "EXTRACTED QUESTION:" followed by the exact question text.
                3. Next write "STEP-BY-STEP SOLUTION:" and guide the student through each logical step so they genuinely learn how to solve it (do not just dump a final answer).
                4. Next write "CONCEPT EXPLANATION:" explaining the underlying principles, formulas, and common exam pitfalls.
            """.trimIndent()

            val res = if (images.isNotEmpty()) {
                GeminiService.extractTextAndAnalyzeImages(images, instruction, profile?.language ?: "English")
            } else {
                GeminiService.sendStudyChatMessage(
                    history = emptyList(),
                    userPrompt = "$instruction\nQuestion: $extraQuestion",
                    language = profile?.language ?: "English",
                    studentClass = profile?.studentClass ?: "Class 12",
                    useHighThinking = true
                ).map { it.first }
            }

            _isBusy.value = false
            res.onSuccess { fullText ->
                val extracted = fullText.substringAfter("EXTRACTED QUESTION:", extraQuestion.ifBlank { "Homework Problem" })
                    .substringBefore("STEP-BY-STEP SOLUTION:").trim().ifBlank { extraQuestion.ifBlank { "Scanned Homework Problem" } }
                val solution = fullText.substringAfter("STEP-BY-STEP SOLUTION:", fullText)
                    .substringBefore("CONCEPT EXPLANATION:").trim()
                val concepts = fullText.substringAfter("CONCEPT EXPLANATION:", "Review the key principles above.").trim()

                repository.createHomeworkSession(
                    userId = currentUserId,
                    subject = subject,
                    extractedQuestion = extracted,
                    stepByStepSolution = solution,
                    conceptExplanation = concepts
                )

                if (profile != null) {
                    repository.recordStudyActivityAndRewards(
                        currentProfile = profile,
                        xpDelta = 15,
                        imagesIncrement = images.size,
                        homeworkDelta = 1,
                        studySessionDelta = 1
                    ).onSuccess { checkAndSurfaceBadges(it) }
                }
                _statusBanner.value = "Homework step-by-step guide ready! +15 XP"
            }.onFailure {
                _errorBanner.value = "Homework OCR failed: ${it.localizedMessage}"
            }
        }
    }

    // --- FOCUS MODE (SYLLABUS OCR + POMODORO TIMER) ---

    fun scanSyllabusAndCreateFocusPlan(
        subject: String,
        topic: String,
        durationMinutes: Int,
        syllabusImages: List<Bitmap>,
        manualSyllabusText: String
    ) {
        if (!isOnline.value) {
            _errorBanner.value = "Offline: Syllabus OCR & AI summary generation require an internet connection. The Pomodoro Focus Timer works offline!"
            return
        }
        val profile = userProfile.value

        viewModelScope.launch {
            _isBusy.value = true
            val prompt = """
                Organize this exam syllabus for subject "$subject" and topic "$topic" ($manualSyllabusText).
                Format output with three sections:
                SUMMARY: Concise overview of syllabus subtopics.
                REVISION NOTES: High-yield bullet points, formulas, and definitions.
                PRACTICE QUESTIONS: 3 exam-style practice questions with brief answers.
            """.trimIndent()

            val res = if (syllabusImages.isNotEmpty()) {
                GeminiService.extractTextAndAnalyzeImages(syllabusImages, prompt, profile?.language ?: "English")
            } else {
                GeminiService.sendStudyChatMessage(
                    history = emptyList(),
                    userPrompt = prompt,
                    language = profile?.language ?: "English",
                    studentClass = profile?.studentClass ?: "Class 12",
                    useHighThinking = true
                ).map { it.first }
            }
            _isBusy.value = false

            res.onSuccess { text ->
                val summary = text.substringAfter("SUMMARY:", text).substringBefore("REVISION NOTES:").trim()
                val notes = text.substringAfter("REVISION NOTES:", "").substringBefore("PRACTICE QUESTIONS:").trim()
                val questions = text.substringAfter("PRACTICE QUESTIONS:", "").trim()

                repository.createFocusSession(
                    userId = currentUserId,
                    subject = subject,
                    topic = topic,
                    syllabusSummary = summary.ifBlank { text.take(600) },
                    revisionNotes = notes.ifBlank { "Key concepts extracted." },
                    practiceQuestions = questions.ifBlank { "1. Define core principles of $topic." },
                    durationMinutes = durationMinutes,
                    completed = false,
                    xpAwarded = false
                )

                if (profile != null) {
                    repository.recordStudyActivityAndRewards(
                        currentProfile = profile,
                        xpDelta = 10,
                        imagesIncrement = syllabusImages.size,
                        syllabusTopicsDelta = 1
                    ).onSuccess { checkAndSurfaceBadges(it) }
                }
                _statusBanner.value = "Syllabus scanned & organized into Focus Mode!"
            }.onFailure {
                _errorBanner.value = "Syllabus scan failed: ${it.localizedMessage}"
            }
        }
    }

    fun configurePomodoroDurations(focusMin: Int, breakMin: Int) {
        val safeFocus = focusMin.coerceIn(5, 120)
        val safeBreak = breakMin.coerceIn(1, 30)
        _focusDurationMinutes.value = safeFocus
        _breakDurationMinutes.value = safeBreak
        if (!_timerRunning.value) {
            _timerIsBreak.value = false
            _timerSecondsLeft.value = safeFocus * 60
        }
    }

    fun togglePomodoroTimer() {
        if (_timerRunning.value) {
            _timerRunning.value = false
            timerJob?.cancel()
            SpotifyMusicService.onPomodoroFocusPaused()
        } else {
            _timerRunning.value = true
            if (!_timerIsBreak.value) {
                SpotifyMusicService.onPomodoroFocusStarted()
            }
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_timerRunning.value && _timerSecondsLeft.value > 0) {
                    delay(1000L)
                    _timerSecondsLeft.value -= 1
                }
                if (_timerSecondsLeft.value <= 0) {
                    _timerRunning.value = false
                    if (!_timerIsBreak.value) {
                        SpotifyMusicService.onPomodoroFocusEnded(isTransitionToBreak = true)
                        completePomodoroFocusSession(_focusDurationMinutes.value)
                        _timerIsBreak.value = true
                        _timerSecondsLeft.value = _breakDurationMinutes.value * 60
                    } else {
                        _timerIsBreak.value = false
                        _timerSecondsLeft.value = _focusDurationMinutes.value * 60
                        _statusBanner.value = "Break finished! Ready for your next Focus block."
                    }
                }
            }
        }
    }

    fun resetPomodoroTimer() {
        _timerRunning.value = false
        timerJob?.cancel()
        _timerIsBreak.value = false
        _timerSecondsLeft.value = _focusDurationMinutes.value * 60
        SpotifyMusicService.onPomodoroFocusEnded(isTransitionToBreak = false)
    }

    fun completePomodoroFocusSession(minutesCompleted: Int, focusRecord: FocusSessionRecord? = null) {
        viewModelScope.launch {
            if (focusRecord != null) {
                repository.markFocusTopicCompleted(
                    userId = currentUserId,
                    sessionId = focusRecord.id,
                    completed = true,
                    xpAwarded = true
                )
            }
            val profile = userProfile.value
            if (profile != null) {
                val badgeRes = repository.recordStudyActivityAndRewards(
                    currentProfile = profile,
                    xpDelta = 20, // +20 XP per Focus Session
                    focusSessionsDelta = 1,
                    studyMinutesDelta = minutesCompleted,
                    studySessionDelta = 1,
                    syllabusTopicsDelta = if (focusRecord != null && !focusRecord.completed) 1 else 0
                )
                badgeRes.onSuccess { checkAndSurfaceBadges(it) }
                _statusBanner.value = "Focus session complete! +20 XP earned."
            }
        }
    }

    // --- MULTIMODAL CREATIVE & SCIENCE LAB (IMAGES, VEO VIDEO, LYRIA MUSIC, LIVE VOICE, AUDIO TRANSCRIBE) ---

    fun generateDiagramOrStudyImage(
        prompt: String,
        aspectRatio: String,
        imageSize: String,
        useStudioPro: Boolean,
        referenceBitmap: Bitmap? = null,
        styleOrLayout: String = "Scientific Diagram"
    ) {
        val cleanPrompt = prompt.trim().ifBlank { "Labeled diagram of a eukaryotic cell structure" }
        if (!GeminiService.isSafeAcademicPrompt(cleanPrompt)) {
            _errorBanner.value = "Academic Safety Policy: Abusive or inappropriate prompts are blocked."
            return
        }
        val jobId = "job_img_${System.currentTimeMillis() % 100000}"
        enqueueAndUpdateMediaJob(
            MediaGenerationJob(
                jobId = jobId,
                mediaType = "IMAGE_OR_DIAGRAM",
                prompt = cleanPrompt,
                styleOrLayout = styleOrLayout,
                aspectRatio = aspectRatio,
                resolutionOrDuration = imageSize,
                status = "QUEUED",
                progressPercent = 20
            )
        )
        activeMediaGenJob = viewModelScope.launch {
            _isBusy.value = true
            enqueueAndUpdateMediaJob(
                MediaGenerationJob(
                    jobId = jobId,
                    mediaType = "IMAGE_OR_DIAGRAM",
                    prompt = cleanPrompt,
                    styleOrLayout = styleOrLayout,
                    aspectRatio = aspectRatio,
                    resolutionOrDuration = imageSize,
                    status = "PROCESSING",
                    progressPercent = 65
                )
            )
            val res = GeminiService.generateStudyDiagramOrImage(
                prompt = cleanPrompt,
                aspectRatio = aspectRatio,
                imageSize = imageSize,
                useStudioProModel = useStudioPro,
                referenceBitmap = referenceBitmap,
                language = userProfile.value?.language ?: "English",
                styleOrLayout = styleOrLayout,
                studentClass = userProfile.value?.studentClass ?: "Class 10"
            )
            _isBusy.value = false
            res.onSuccess { (bmp, info) ->
                _generatedDiagramBitmap.value = bmp
                _generatedMediaInfo.value = info
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "IMAGE_OR_DIAGRAM",
                        prompt = cleanPrompt,
                        styleOrLayout = styleOrLayout,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = imageSize,
                        status = "COMPLETED",
                        progressPercent = 100,
                        resultSummary = info.take(240)
                    )
                )
                _statusBanner.value = "Generated $styleOrLayout ($aspectRatio • $imageSize)."
            }.onFailure {
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "IMAGE_OR_DIAGRAM",
                        prompt = cleanPrompt,
                        styleOrLayout = styleOrLayout,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = imageSize,
                        status = "FAILED",
                        progressPercent = 0,
                        errorMessage = it.localizedMessage ?: "Failed"
                    )
                )
                _errorBanner.value = "Image generation failed: ${it.localizedMessage}"
            }
        }
    }

    fun generateVeoEducationalVideo(
        prompt: String,
        aspectRatio: String,
        photo: Bitmap? = null,
        style: String = "3D Academic Simulation",
        durationSeconds: Int = 8
    ) {
        val cleanPrompt = prompt.trim().ifBlank { "3D animation of planetary orbital mechanics" }
        if (!GeminiService.isSafeAcademicPrompt(cleanPrompt)) {
            _errorBanner.value = "Academic Safety Policy: Abusive or inappropriate prompts are blocked."
            return
        }
        if (!GeminiService.isEducationalVideoTopic(cleanPrompt)) {
            _errorBanner.value = "Educational Purpose Policy: Videos in Ultron must be related to academic & educational subjects."
            return
        }
        val jobId = "job_veo_${System.currentTimeMillis() % 100000}"
        enqueueAndUpdateMediaJob(
            MediaGenerationJob(
                jobId = jobId,
                mediaType = "VIDEO",
                prompt = cleanPrompt,
                styleOrLayout = style,
                aspectRatio = aspectRatio,
                resolutionOrDuration = "${durationSeconds}s",
                status = "QUEUED",
                progressPercent = 20
            )
        )
        activeMediaGenJob = viewModelScope.launch {
            _isBusy.value = true
            enqueueAndUpdateMediaJob(
                MediaGenerationJob(
                    jobId = jobId,
                    mediaType = "VIDEO",
                    prompt = cleanPrompt,
                    styleOrLayout = style,
                    aspectRatio = aspectRatio,
                    resolutionOrDuration = "${durationSeconds}s",
                    status = "PROCESSING",
                    progressPercent = 65
                )
            )
            val lang = userProfile.value?.language ?: "English"
            val studentClass = userProfile.value?.studentClass ?: "Class 10"
            val res = GeminiService.generateEducationalVideoForStudyChat(
                prompt = cleanPrompt,
                language = lang,
                aspectRatio = aspectRatio,
                sourcePhoto = photo,
                durationSeconds = durationSeconds,
                videoStyle = style,
                studentClass = studentClass
            )
            _isBusy.value = false
            res.onSuccess { (info, scenes) ->
                _generatedMediaInfo.value = info
                _generatedVideoScenes.value = scenes
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "VIDEO",
                        prompt = cleanPrompt,
                        styleOrLayout = style,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = "${durationSeconds}s",
                        status = "COMPLETED",
                        progressPercent = 100,
                        resultSummary = info.take(240)
                    )
                )
                _statusBanner.value = "Veo 3.1 educational video (${durationSeconds}s • $style) & interactive 4-scene lesson ready!"
            }.onFailure {
                enqueueAndUpdateMediaJob(
                    MediaGenerationJob(
                        jobId = jobId,
                        mediaType = "VIDEO",
                        prompt = cleanPrompt,
                        styleOrLayout = style,
                        aspectRatio = aspectRatio,
                        resolutionOrDuration = "${durationSeconds}s",
                        status = "FAILED",
                        progressPercent = 0,
                        errorMessage = it.localizedMessage ?: "Failed"
                    )
                )
                _errorBanner.value = "Veo video generation error: ${it.localizedMessage}"
            }
        }
    }

    fun analyzeEducationalVideo(videoUrlOrTitle: String, question: String) {
        if (!isOnline.value) {
            _errorBanner.value = "Offline: Video analysis requires an internet connection."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = GeminiService.analyzeVideoContent(
                videoBase64 = null,
                videoTitleOrUrl = videoUrlOrTitle,
                question = question,
                language = userProfile.value?.language ?: "English"
            )
            _isBusy.value = false
            res.onSuccess { analysis ->
                _generatedMediaInfo.value = analysis
                _statusBanner.value = "Video analysis complete (gemini-3.1-pro-preview)."
            }.onFailure {
                _errorBanner.value = "Video analysis failed: ${it.localizedMessage}"
            }
        }
    }

    fun generateFocusMusic(prompt: String, fullLength: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = GeminiService.generateStudyMusicWithLyria(prompt, fullLength)
            _isBusy.value = false
            res.onSuccess { info ->
                _generatedMediaInfo.value = info
                _statusBanner.value = info
            }.onFailure {
                _errorBanner.value = "Music generation failed: ${it.localizedMessage}"
            }
        }
    }

    fun transcribeAudioBytes(audioBase64: String, onTranscribed: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = GeminiService.transcribeStudyAudio(
                audioBase64 = audioBase64,
                language = userProfile.value?.language ?: "English"
            )
            _isBusy.value = false
            res.onSuccess { text ->
                if (text.isNotBlank()) onTranscribed(text)
            }.onFailure {
                _errorBanner.value = "Transcription failed: ${it.localizedMessage}"
            }
        }
    }

    fun sendLiveVoicePrompt(spokenText: String, tts: TextToSpeech?) {
        if (spokenText.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val lang = userProfile.value?.language ?: "English"
            val res = GeminiService.sendLiveVoiceTurn(spokenText, lang)
            _isBusy.value = false
            res.onSuccess { reply ->
                _generatedMediaInfo.value = "Live Voice ($lang): $reply"
                GeminiService.synthesizeSpeechWithGeminiTts(reply)
                tts?.speak(reply.take(400), TextToSpeech.QUEUE_FLUSH, null, "ultron_live_voice")
            }.onFailure {
                _errorBanner.value = "Live Voice session error: ${it.localizedMessage}"
            }
        }
    }

    // --- MANUAL UPI PAYMENT SUBMISSION & ADMIN APPROVAL ---

    fun submitManualUpiPayment(selectedPlan: String, amount: Int, utr: String) {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val emailNotificationStatus = GeminiService.sendAdminPaymentNotificationEmail(
                studentName = profile.fullName,
                studentEmail = profile.email,
                selectedPlan = selectedPlan,
                amount = amount,
                utr = utr,
                submissionTime = timeStr
            )
            val res = repository.submitUpiPaymentRequest(
                userId = currentUserId,
                studentName = profile.fullName,
                studentEmail = profile.email,
                selectedPlan = selectedPlan,
                amount = amount,
                utr = utr,
                notificationStatus = emailNotificationStatus
            )
            _isBusy.value = false
            res.onSuccess { reqId ->
                _statusBanner.value = "Payment request (#${reqId.take(6)}) saved as PENDING! Both official admins have been notified for verification."
            }.onFailure {
                _errorBanner.value = it.localizedMessage ?: "Failed to submit payment request."
            }
        }
    }

    fun adminVerifyPayment(request: PaymentRequest, approve: Boolean, note: String) {
        val profile = userProfile.value ?: return
        if (!profile.isAdminUser) {
            _errorBanner.value = "Unauthorized: Only official Ultron administrators can approve or reject payments."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val statusNote = if (approve) "APPROVED_AND_ACTIVATED" else "REJECTED_BY_ADMIN"
            val res = repository.adminProcessPaymentRequest(
                request = request,
                approve = approve,
                adminEmail = profile.email,
                adminNote = note.ifBlank { if (approve) "Verified UPI UTR" else "UTR could not be verified" },
                notificationStatus = statusNote
            )
            _isBusy.value = false
            res.onSuccess {
                _statusBanner.value = "Payment request ${if (approve) "APPROVED & Subscription Activated" else "REJECTED"}."
            }.onFailure {
                _errorBanner.value = "Admin action failed: ${it.localizedMessage}"
            }
        }
    }

    // --- COLLABORATIVE NOTEBOOKS & SCHOOL PORTALS ---

    fun createOneClickNotebook(
        title: String,
        subject: String,
        sourceType: String,
        sourceUrl: String,
        rawNotesOrPrompt: String,
        publishToCommunity: Boolean
    ) {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val structuredContent = if (isOnline.value && sourceUrl.isNotBlank()) {
                GeminiService.sendStudyChatMessage(
                    history = emptyList(),
                    userPrompt = "Create a structured, collaborative student notebook from $sourceType ($sourceUrl) on '$title' ($subject). Additional notes: $rawNotesOrPrompt. Include Key Takeaways, Structured Summary, Formulas/Diagrams, and Peer Discussion Questions.",
                    language = profile.language,
                    studentClass = profile.studentClass,
                    useGoogleSearch = sourceType == "WEBSITE"
                ).getOrNull()?.first ?: rawNotesOrPrompt
            } else {
                rawNotesOrPrompt.ifBlank { "Structured study notebook for $title ($subject)." }
            }

            val res = repository.createSharedNotebook(
                userId = currentUserId,
                authorName = profile.fullName,
                title = title,
                subject = subject,
                sourceType = sourceType,
                sourceUrl = sourceUrl,
                content = structuredContent,
                isPublic = publishToCommunity
            )
            _isBusy.value = false
            res.onSuccess {
                _statusBanner.value = "1-Click Study Notebook created & saved to cloud!"
            }.onFailure {
                _errorBanner.value = "Failed to save notebook: ${it.localizedMessage}"
            }
        }
    }

    fun postNotebookCommentOrLike(notebook: SharedNotebook, comment: String, like: Boolean) {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            repository.addNotebookFeedbackOrChat(
                notebook = notebook,
                senderName = profile.fullName,
                message = comment,
                incrementLike = like
            )
        }
    }

    fun createSchoolPortalGroup(
        schoolName: String,
        groupName: String,
        description: String,
        announcement: String,
        academicYear: String = "2026-2027",
        classesInfo: String = "Class 6 to Class 12",
        subjectsInfo: String = "Mathematics • Physics • Chemistry • Biology • Computer Science",
        timetableInfo: String = "Mon-Fri: 09:00 AM - 03:30 PM | Doubt Clearing: 04:00 PM",
        homeworkInfo: String = "Chapter Practice Problems & Weekly Lab Report",
        groupCategory: String = "CLASS_GROUP",
        requestSuperAdminApproval: Boolean = false
    ) {
        val profile = userProfile.value ?: return
        val effectiveRole = if (profile.isAdminUser) "ADMIN" else profile.role
        if (effectiveRole !in listOf("ADMIN", "TEACHER", "PRINCIPAL")) {
            _errorBanner.value = "RBAC Policy: Only Admins, Teachers, or Principals can create School Portals."
            return
        }
        val initialStatus = if (requestSuperAdminApproval && !profile.isAdminUser) "PENDING_APPROVAL" else "APPROVED"
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createSchoolPortal(
                creatorId = currentUserId,
                creatorName = profile.fullName,
                creatorRole = effectiveRole,
                schoolName = schoolName.ifBlank { profile.schoolName },
                groupName = groupName,
                description = description,
                announcement = announcement,
                academicYear = academicYear,
                classesInfo = classesInfo,
                subjectsInfo = subjectsInfo,
                timetableInfo = timetableInfo,
                homeworkInfo = homeworkInfo,
                groupCategory = groupCategory,
                portalStatus = initialStatus
            )
            _isBusy.value = false
            res.onSuccess {
                _statusBanner.value = if (initialStatus == "PENDING_APPROVAL") {
                    "School Portal request submitted! Pending Super Admin approval."
                } else {
                    "School Portal & Group Workspace created!"
                }
            }.onFailure {
                _errorBanner.value = "Failed to create school portal: ${it.localizedMessage}"
            }
        }
    }

    fun sendPortalGroupMessage(
        portal: SchoolPortal,
        message: String = "",
        sharedFile: String = "",
        toggleVoice: Boolean? = null,
        updatedAnnouncement: String? = null,
        updatedTimetable: String? = null,
        updatedHomework: String? = null,
        moderationActionNote: String? = null
    ) {
        val profile = userProfile.value ?: return
        if (message.isNotBlank() && !GeminiService.isSafeAcademicPrompt(message)) {
            _errorBanner.value = "School Portal Moderation: Inappropriate or abusive messages are not permitted."
            return
        }
        viewModelScope.launch {
            repository.postSchoolPortalChatOrToggleVoice(
                portal = portal,
                senderName = profile.fullName,
                senderRole = if (profile.isAdminUser) "ADMIN" else profile.role,
                message = message,
                sharedFileTitle = sharedFile,
                toggleVoice = toggleVoice,
                updatedAnnouncement = updatedAnnouncement,
                updatedTimetable = updatedTimetable,
                updatedHomework = updatedHomework,
                moderationActionNote = moderationActionNote
            )
        }
    }

    fun adminReviewSchoolPortal(
        portal: SchoolPortal,
        newStatus: String,
        moderationReason: String
    ) {
        val profile = userProfile.value ?: return
        if (!profile.isAdminUser) {
            _errorBanner.value = "Unauthorized: Only Super Admins can approve, reject, suspend, or revoke School Portals."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminReviewOrModeratePortal(
                portal = portal,
                newStatus = newStatus,
                adminEmail = profile.email,
                moderationReason = moderationReason
            )
            _isBusy.value = false
            res.onSuccess {
                _statusBanner.value = "School Portal '${portal.schoolName} - ${portal.groupName}' status updated to $newStatus."
            }.onFailure {
                _errorBanner.value = "Portal review failed: ${it.localizedMessage}"
            }
        }
    }
}
