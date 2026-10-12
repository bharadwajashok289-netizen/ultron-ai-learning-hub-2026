package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BadgeDefinition
import com.example.data.model.FlashcardDeck
import com.example.data.model.FocusSessionRecord
import com.example.data.model.HomeworkSession
import com.example.data.model.PaymentRequest
import com.example.data.model.SchoolPortal
import com.example.data.model.SharedNotebook
import com.example.data.model.StudyConversation
import com.example.data.model.StudyQuiz
import com.example.data.model.StudyTask
import com.example.data.model.UltronAdmins
import com.example.data.model.UltronBadges
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { value, error ->
        if (error != null) {
            close(error)
        } else if (value != null) {
            trySend(value)
        }
    }
    awaitClose { registration.remove() }
}

private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { value, error ->
        if (error != null) {
            close(error)
        } else if (value != null) {
            trySend(value)
        }
    }
    awaitClose { registration.remove() }
}

class UltronRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun currentYearMonth(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date())
    }

    fun currentLocalDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun previousLocalDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    // --- 1. USER PROFILE & ONBOARDING ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun getOrCreateProfile(
        userId: String,
        defaultName: String,
        defaultEmail: String
    ): Result<UserProfile> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = userId, fullName = defaultName, email = defaultEmail)
                // Check monthly usage reset
                val ym = currentYearMonth()
                if (existing.usageMonth != ym) {
                    docRef.update(
                        mapOf(
                            "chatsUsed" to 0,
                            "imagesUsed" to 0,
                            "usageMonth" to ym,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return@runCatching existing.copy(chatsUsed = 0, imagesUsed = 0, usageMonth = ym)
                }
                existing
            } else {
                val isAdmin = UltronAdmins.isOfficialAdmin(defaultEmail)
                val initialProfile = UserProfile(
                    id = userId,
                    userId = userId,
                    fullName = defaultName.ifBlank { "Ultron Scholar" },
                    email = defaultEmail.ifBlank { "student@ultron.edu" },
                    studentClass = "Class 12",
                    section = "A",
                    schoolName = "Ultron Academy",
                    dob = "2008-05-15",
                    age = 17,
                    role = if (isAdmin) "ADMIN" else "STUDENT",
                    language = "English",
                    themePreference = "DARK",
                    plan = if (isAdmin) "PRO" else "FREE",
                    subscriptionStatus = "ACTIVE",
                    usageMonth = currentYearMonth(),
                    lastStudyDate = currentLocalDateString(),
                    onboardingCompleted = false
                )
                docRef.set(initialProfile.toCreateMap()).await()
                initialProfile
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateProfileDetails(
        userId: String,
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
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            val safeRole = if (role in listOf("STUDENT", "TEACHER", "PRINCIPAL")) role else "STUDENT"
            val updates = mutableMapOf<String, Any>(
                "fullName" to fullName.trim().take(120).ifBlank { "Student" },
                "studentClass" to studentClass.trim().take(40),
                "section" to section.trim().take(20),
                "schoolName" to schoolName.trim().take(160),
                "dob" to dob.trim().take(30),
                "age" to age.coerceIn(5, 100),
                "language" to language.trim().take(40),
                "themePreference" to themePreference,
                "onboardingCompleted" to onboardingCompleted,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            // Keep ADMIN role if already admin
            if (role != "ADMIN") {
                updates["role"] = safeRole
            }
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordStudyActivityAndRewards(
        currentProfile: UserProfile,
        xpDelta: Int = 0,
        chatsIncrement: Int = 0,
        imagesIncrement: Int = 0,
        questionsDelta: Int = 0,
        quizzesDelta: Int = 0,
        latestQuizScore: Int = -1,
        flashcardsReviewedDelta: Int = 0,
        flashcardsMasteredDelta: Int = 0,
        homeworkDelta: Int = 0,
        focusSessionsDelta: Int = 0,
        studyMinutesDelta: Int = 0,
        tasksCompletedDelta: Int = 0,
        studySessionDelta: Int = 0,
        syllabusTopicsDelta: Int = 0
    ): Result<List<BadgeDefinition>> = runCatching {
        val uid = currentProfile.userId.ifBlank { requireUserId() }
        val docRef = db.collection("users").document(uid)
        val today = currentLocalDateString()
        val yesterday = previousLocalDateString()
        val ym = currentYearMonth()

        val newStreak = when (currentProfile.lastStudyDate) {
            today -> currentProfile.currentStreak.coerceAtLeast(1)
            yesterday -> currentProfile.currentStreak + 1
            else -> 1
        }
        val streakBonusXp = if (currentProfile.lastStudyDate != today && currentProfile.lastStudyDate.isNotBlank()) 5 else 0
        val newLongest = maxOf(currentProfile.longestStreak, newStreak)

        val baseChats = if (currentProfile.usageMonth == ym) currentProfile.chatsUsed else 0
        val baseImages = if (currentProfile.usageMonth == ym) currentProfile.imagesUsed else 0

        val updatedDraft = currentProfile.copy(
            chatsUsed = baseChats + chatsIncrement,
            imagesUsed = baseImages + imagesIncrement,
            usageMonth = ym,
            currentStreak = newStreak,
            longestStreak = newLongest,
            lastStudyDate = today,
            questionsAnswered = currentProfile.questionsAnswered + questionsDelta,
            quizzesCompleted = currentProfile.quizzesCompleted + quizzesDelta,
            flashcardsReviewed = currentProfile.flashcardsReviewed + flashcardsReviewedDelta,
            flashcardsMastered = currentProfile.flashcardsMastered + flashcardsMasteredDelta,
            homeworkSessions = currentProfile.homeworkSessions + homeworkDelta,
            focusSessions = currentProfile.focusSessions + focusSessionsDelta,
            studyMinutes = currentProfile.studyMinutes + studyMinutesDelta,
            tasksCompleted = currentProfile.tasksCompleted + tasksCompletedDelta,
            totalStudySessions = currentProfile.totalStudySessions + studySessionDelta,
            syllabusTopicsCompleted = currentProfile.syllabusTopicsCompleted + syllabusTopicsDelta
        )

        val newlyUnlocked = UltronBadges.evaluateUnlockedBadges(updatedDraft, latestQuizScore)
        val badgeXpBonus = newlyUnlocked.sumOf { it.xpReward }
        val finalXp = currentProfile.xp + xpDelta + streakBonusXp + badgeXpBonus
        val finalLevel = (finalXp / 100) + 1
        val finalBadges = (currentProfile.unlockedBadges + newlyUnlocked.map { it.id }).distinct().take(30)

        try {
            docRef.update(
                mapOf(
                    "chatsUsed" to updatedDraft.chatsUsed,
                    "imagesUsed" to updatedDraft.imagesUsed,
                    "usageMonth" to ym,
                    "xp" to finalXp,
                    "level" to finalLevel,
                    "currentStreak" to newStreak,
                    "longestStreak" to newLongest,
                    "lastStudyDate" to today,
                    "unlockedBadges" to finalBadges,
                    "questionsAnswered" to updatedDraft.questionsAnswered,
                    "quizzesCompleted" to updatedDraft.quizzesCompleted,
                    "flashcardsReviewed" to updatedDraft.flashcardsReviewed,
                    "flashcardsMastered" to updatedDraft.flashcardsMastered,
                    "homeworkSessions" to updatedDraft.homeworkSessions,
                    "focusSessions" to updatedDraft.focusSessions,
                    "studyMinutes" to updatedDraft.studyMinutes,
                    "tasksCompleted" to updatedDraft.tasksCompleted,
                    "totalStudySessions" to updatedDraft.totalStudySessions,
                    "syllabusTopicsCompleted" to updatedDraft.syllabusTopicsCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            newlyUnlocked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 2. TASKS ---

    fun observeTasks(userId: String): Flow<List<StudyTask>> {
        val path = "users/$userId/tasks"
        return db.collection("users").document(userId).collection("tasks")
            .snapshots()
            .map { snap ->
                snap.toObjects(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createTask(
        title: String,
        subject: String = "General",
        deadline: String = "Today",
        priority: String = "MEDIUM",
        category: String = "TODAY"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("tasks")
        try {
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().take(200),
                "subject" to subject.trim().take(80).ifBlank { "General" },
                "deadline" to deadline.trim().take(40),
                "priority" to if (priority in listOf("HIGH", "MEDIUM", "LOW")) priority else "MEDIUM",
                "category" to if (category in listOf("TODAY", "TOMORROW", "UPCOMING")) category else "TODAY",
                "completed" to false,
                "xpAwarded" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val doc = colRef.add(payload).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun getUserTasks(): Result<List<StudyTask>> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("tasks")
        try {
            val snap = colRef.get().await()
            snap.toObjects(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun getTaskById(ownerUserId: String, taskId: String): Result<StudyTask?> = runCatching {
        val docRef = db.collection("users").document(ownerUserId).collection("tasks").document(taskId)
        try {
            val snap = docRef.get().await()
            snap.toObject(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateTask(userId: String, task: StudyTask): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId).collection("tasks").document(task.id)
        try {
            docRef.update(
                mapOf(
                    "title" to task.title.take(200),
                    "subject" to task.subject.take(80),
                    "deadline" to task.deadline.take(40),
                    "priority" to task.priority,
                    "category" to task.category,
                    "completed" to task.completed,
                    "xpAwarded" to task.xpAwarded,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId).collection("tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. STUDY CONVERSATIONS ---

    fun observeConversations(userId: String): Flow<List<StudyConversation>> {
        val path = "users/$userId/conversations"
        return db.collection("users").document(userId).collection("conversations")
            .snapshots()
            .map { snap ->
                snap.toObjects(StudyConversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { (it.updatedAt ?: it.createdAt)?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun saveConversation(
        userId: String,
        conversationId: String?,
        title: String,
        language: String,
        lastPreview: String,
        messageCount: Int,
        messagesPayload: String
    ): Result<String> = runCatching {
        val colRef = db.collection("users").document(userId).collection("conversations")
        try {
            if (conversationId.isNullOrBlank()) {
                val doc = colRef.add(
                    mapOf(
                        "userId" to userId,
                        "title" to title.take(200).ifBlank { "Study Session" },
                        "language" to language.take(40),
                        "lastMessagePreview" to lastPreview.take(500),
                        "messageCount" to messageCount,
                        "messagesPayload" to messagesPayload.take(100000),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                doc.id
            } else {
                colRef.document(conversationId).update(
                    mapOf(
                        "title" to title.take(200),
                        "language" to language.take(40),
                        "lastMessagePreview" to lastPreview.take(500),
                        "messageCount" to messageCount,
                        "messagesPayload" to messagesPayload.take(100000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                conversationId
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colRef.path)
            throw e
        }
    }

    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId).collection("conversations").document(conversationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. FLASHCARD DECKS ---

    fun observeFlashcardDecks(userId: String): Flow<List<FlashcardDeck>> {
        val path = "users/$userId/flashcard_decks"
        return db.collection("users").document(userId).collection("flashcard_decks")
            .snapshots()
            .map { snap ->
                snap.toObjects(FlashcardDeck::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createFlashcardDeck(
        userId: String,
        name: String,
        subject: String,
        totalCards: Int,
        cardsPayload: String
    ): Result<String> = runCatching {
        val colRef = db.collection("users").document(userId).collection("flashcard_decks")
        try {
            val doc = colRef.add(
                mapOf(
                    "userId" to userId,
                    "name" to name.trim().take(150).ifBlank { "Study Deck" },
                    "subject" to subject.trim().take(80).ifBlank { "General" },
                    "totalCards" to totalCards.coerceIn(1, 200),
                    "masteredCards" to 0,
                    "cardsPayload" to cardsPayload.take(80000),
                    "xpAwarded" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun updateFlashcardDeckProgress(
        userId: String,
        deckId: String,
        totalCards: Int,
        masteredCards: Int,
        cardsPayload: String,
        xpAwarded: Boolean
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId).collection("flashcard_decks").document(deckId)
        try {
            docRef.update(
                mapOf(
                    "totalCards" to totalCards.coerceIn(1, 200),
                    "masteredCards" to masteredCards.coerceIn(0, totalCards),
                    "cardsPayload" to cardsPayload.take(80000),
                    "xpAwarded" to xpAwarded,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 5. QUIZZES ---

    fun observeQuizzes(userId: String): Flow<List<StudyQuiz>> {
        val path = "users/$userId/quizzes"
        return db.collection("users").document(userId).collection("quizzes")
            .snapshots()
            .map { snap ->
                snap.toObjects(StudyQuiz::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createQuiz(
        userId: String,
        topic: String,
        difficulty: String,
        totalQuestions: Int,
        questionsPayload: String
    ): Result<String> = runCatching {
        val colRef = db.collection("users").document(userId).collection("quizzes")
        try {
            val safeDiff = if (difficulty in listOf("Easy", "Medium", "Hard")) difficulty else "Medium"
            val doc = colRef.add(
                mapOf(
                    "userId" to userId,
                    "topic" to topic.trim().take(200).ifBlank { "General Quiz" },
                    "difficulty" to safeDiff,
                    "totalQuestions" to totalQuestions.coerceIn(1, 50),
                    "correctAnswers" to 0,
                    "scorePercent" to 0,
                    "questionsPayload" to questionsPayload.take(80000),
                    "completed" to false,
                    "xpAwarded" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun submitQuizResult(
        userId: String,
        quizId: String,
        correctAnswers: Int,
        scorePercent: Int,
        questionsPayload: String,
        xpAwarded: Boolean
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId).collection("quizzes").document(quizId)
        try {
            docRef.update(
                mapOf(
                    "correctAnswers" to correctAnswers,
                    "scorePercent" to scorePercent.coerceIn(0, 100),
                    "questionsPayload" to questionsPayload.take(80000),
                    "completed" to true,
                    "xpAwarded" to xpAwarded,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 6. FOCUS MODE & SYLLABUS SESSIONS ---

    fun observeFocusSessions(userId: String): Flow<List<FocusSessionRecord>> {
        val path = "users/$userId/focus_sessions"
        return db.collection("users").document(userId).collection("focus_sessions")
            .snapshots()
            .map { snap ->
                snap.toObjects(FocusSessionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createFocusSession(
        userId: String,
        subject: String,
        topic: String,
        syllabusSummary: String,
        revisionNotes: String,
        practiceQuestions: String,
        durationMinutes: Int,
        completed: Boolean,
        xpAwarded: Boolean
    ): Result<String> = runCatching {
        val colRef = db.collection("users").document(userId).collection("focus_sessions")
        try {
            val doc = colRef.add(
                mapOf(
                    "userId" to userId,
                    "subject" to subject.trim().take(100).ifBlank { "General" },
                    "topic" to topic.trim().take(200).ifBlank { "Focus Session" },
                    "syllabusSummary" to syllabusSummary.take(20000),
                    "revisionNotes" to revisionNotes.take(20000),
                    "practiceQuestions" to practiceQuestions.take(20000),
                    "durationMinutes" to durationMinutes.coerceIn(1, 300),
                    "completed" to completed,
                    "xpAwarded" to xpAwarded,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun markFocusTopicCompleted(
        userId: String,
        sessionId: String,
        completed: Boolean,
        xpAwarded: Boolean
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId).collection("focus_sessions").document(sessionId)
        try {
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "xpAwarded" to xpAwarded,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 7. HOMEWORK ASSIST SESSIONS ---

    fun observeHomeworkSessions(userId: String): Flow<List<HomeworkSession>> {
        val path = "users/$userId/homework_sessions"
        return db.collection("users").document(userId).collection("homework_sessions")
            .snapshots()
            .map { snap ->
                snap.toObjects(HomeworkSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createHomeworkSession(
        userId: String,
        subject: String,
        extractedQuestion: String,
        stepByStepSolution: String,
        conceptExplanation: String,
        followUpNotes: String = ""
    ): Result<String> = runCatching {
        val colRef = db.collection("users").document(userId).collection("homework_sessions")
        try {
            val doc = colRef.add(
                mapOf(
                    "userId" to userId,
                    "subject" to subject.trim().take(100).ifBlank { "Homework" },
                    "extractedQuestion" to extractedQuestion.take(10000).ifBlank { "Uploaded Homework Problem" },
                    "stepByStepSolution" to stepByStepSolution.take(30000),
                    "conceptExplanation" to conceptExplanation.take(20000),
                    "followUpNotes" to followUpNotes.take(20000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    // --- 8. MANUAL UPI PAYMENT SYSTEM & ADMIN VERIFICATION ---

    fun observeUserPaymentRequests(userId: String): Flow<List<PaymentRequest>> {
        val path = "payment_requests"
        return db.collection("payment_requests")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllPaymentRequestsForAdmin(): Flow<List<PaymentRequest>> {
        val path = "payment_requests"
        return db.collection("payment_requests")
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection("users")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.xp }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun submitUpiPaymentRequest(
        userId: String,
        studentName: String,
        studentEmail: String,
        selectedPlan: String,
        amount: Int,
        utr: String,
        notificationStatus: String
    ): Result<String> = runCatching {
        val colRef = db.collection("payment_requests")
        try {
            val cleanUtr = utr.trim().take(64)
            require(cleanUtr.length >= 6) { "Please enter a valid 12-digit UTR / Payment Reference ID." }
            val safePlan = if (selectedPlan == "PRO") "PRO" else "BASIC"
            val safeAmount = if (amount in listOf(99, 199, 299, 399)) amount else if (safePlan == "PRO") 299 else 99
            val doc = colRef.add(
                mapOf(
                    "userId" to userId,
                    "studentName" to studentName.trim().take(120).ifBlank { "Student" },
                    "studentEmail" to studentEmail.trim().take(160).ifBlank { "student@ultron.edu" },
                    "selectedPlan" to safePlan,
                    "amount" to safeAmount,
                    "utr" to cleanUtr,
                    "status" to "PENDING",
                    "processedByAdmin" to "",
                    "adminNote" to "",
                    "notificationStatus" to notificationStatus.take(60),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun adminProcessPaymentRequest(
        request: PaymentRequest,
        approve: Boolean,
        adminEmail: String,
        adminNote: String,
        notificationStatus: String
    ): Result<Unit> = runCatching {
        val reqRef = db.collection("payment_requests").document(request.id)
        val userRef = db.collection("users").document(request.userId)
        try {
            val newStatus = if (approve) "APPROVED" else "REJECTED"
            reqRef.update(
                mapOf(
                    "status" to newStatus,
                    "processedByAdmin" to adminEmail.take(160),
                    "adminNote" to adminNote.take(500),
                    "notificationStatus" to notificationStatus.take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                val startStr = currentLocalDateString()
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 30)
                val expiryStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                userRef.update(
                    mapOf(
                        "plan" to request.selectedPlan,
                        "subscriptionStatus" to "ACTIVE",
                        "subscriptionStartDate" to startStr,
                        "subscriptionExpiryDate" to expiryStr,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            throw e
        }
    }

    // --- 9. COLLABORATIVE NOTEBOOKS & WORKSPACE CHAT ---

    fun observeSharedNotebooks(): Flow<List<SharedNotebook>> {
        val path = "notebooks"
        return db.collection("notebooks")
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(SharedNotebook::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createSharedNotebook(
        userId: String,
        authorName: String,
        title: String,
        subject: String,
        sourceType: String,
        sourceUrl: String,
        content: String,
        isPublic: Boolean = true
    ): Result<String> = runCatching {
        val colRef = db.collection("notebooks")
        try {
            val doc = colRef.add(
                mapOf(
                    "userId" to userId,
                    "authorName" to authorName.trim().take(120).ifBlank { "Scholar" },
                    "title" to title.trim().take(200).ifBlank { "Study Notebook" },
                    "subject" to subject.trim().take(80).ifBlank { "General" },
                    "sourceType" to sourceType.take(40),
                    "sourceUrl" to sourceUrl.take(500),
                    "content" to content.take(80000).ifBlank { "Notes" },
                    "isPublic" to isPublic,
                    "likesCount" to 0,
                    "feedbackLog" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun addNotebookFeedbackOrChat(
        notebook: SharedNotebook,
        senderName: String,
        message: String,
        incrementLike: Boolean = false
    ): Result<Unit> = runCatching {
        val docRef = db.collection("notebooks").document(notebook.id)
        try {
            val entry = if (message.isNotBlank()) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                val prefix = if (notebook.feedbackLog.isBlank()) "" else "${notebook.feedbackLog}\n"
                "$prefix[$timeStr] $senderName: ${message.trim()}"
            } else {
                notebook.feedbackLog
            }
            docRef.update(
                mapOf(
                    "likesCount" to (notebook.likesCount + if (incrementLike) 1 else 0),
                    "feedbackLog" to entry.take(40000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 10. SCHOOL PORTALS & EDUCATOR GROUP CHATS (RBAC) ---

    fun observeSchoolPortals(): Flow<List<SchoolPortal>> {
        val path = "school_portals"
        return db.collection("school_portals")
            .snapshots()
            .map { snap ->
                snap.toObjects(SchoolPortal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createSchoolPortal(
        creatorId: String,
        creatorName: String,
        creatorRole: String,
        schoolName: String,
        groupName: String,
        description: String,
        announcement: String,
        academicYear: String = "2026-2027",
        classesInfo: String = "Class 6 to Class 12",
        subjectsInfo: String = "Mathematics • Physics • Chemistry • Biology • Computer Science",
        timetableInfo: String = "Mon-Fri: 09:00 AM - 03:30 PM | Doubt Clearing: 04:00 PM",
        homeworkInfo: String = "Chapter Practice Problems & Weekly Lab Report",
        groupCategory: String = "OFFICIAL_PORTAL",
        portalStatus: String = "APPROVED"
    ): Result<String> = runCatching {
        val colRef = db.collection("school_portals")
        try {
            require(creatorRole in listOf("ADMIN", "TEACHER", "PRINCIPAL")) {
                "Only Admins, Teachers, or Principals can create a School Portal."
            }
            val safeCategory = if (groupCategory in listOf("OFFICIAL_PORTAL", "CLASS_GROUP", "SUBJECT_GROUP", "STUDENT_STUDY_GROUP", "EXAM_PREP_GROUP")) {
                groupCategory
            } else {
                "OFFICIAL_PORTAL"
            }
            val safeStatus = if (portalStatus in listOf("APPROVED", "PENDING_APPROVAL", "REJECTED", "SUSPENDED", "REVOKED")) {
                portalStatus
            } else {
                "APPROVED"
            }
            val doc = colRef.add(
                mapOf(
                    "creatorId" to creatorId,
                    "creatorName" to creatorName.trim().take(120).ifBlank { "Educator" },
                    "creatorRole" to creatorRole,
                    "schoolName" to schoolName.trim().take(160).ifBlank { "Ultron Partner School" },
                    "groupName" to groupName.trim().take(120).ifBlank { "Academic Group" },
                    "description" to description.trim().take(1000),
                    "announcement" to announcement.trim().take(5000),
                    "sharedFilesInfo" to "Syllabus_2026.pdf • Formula_Sheet.pdf",
                    "chatTranscript" to "[System] Welcome to $groupName ($schoolName) collaborative portal.",
                    "voiceRoomActive" to false,
                    "portalStatus" to safeStatus,
                    "academicYear" to academicYear.trim().take(40).ifBlank { "2026-2027" },
                    "classesInfo" to classesInfo.trim().take(500),
                    "subjectsInfo" to subjectsInfo.trim().take(500),
                    "timetableInfo" to timetableInfo.trim().take(3000),
                    "homeworkInfo" to homeworkInfo.trim().take(3000),
                    "groupCategory" to safeCategory,
                    "moderationLog" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun postSchoolPortalChatOrToggleVoice(
        portal: SchoolPortal,
        senderName: String,
        senderRole: String,
        message: String = "",
        sharedFileTitle: String = "",
        toggleVoice: Boolean? = null,
        updatedAnnouncement: String? = null,
        updatedTimetable: String? = null,
        updatedHomework: String? = null,
        moderationActionNote: String? = null
    ): Result<Unit> = runCatching {
        val docRef = db.collection("school_portals").document(portal.id)
        try {
            val newChat = if (message.isNotBlank()) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                "${portal.chatTranscript}\n[$timeStr] $senderName ($senderRole): ${message.trim()}".takeLast(55000)
            } else {
                portal.chatTranscript
            }
            val newFiles = if (sharedFileTitle.isNotBlank()) {
                if (portal.sharedFilesInfo.isBlank()) sharedFileTitle else "${portal.sharedFilesInfo} • $sharedFileTitle"
            } else {
                portal.sharedFilesInfo
            }
            val newVoice = toggleVoice ?: portal.voiceRoomActive
            val updates = mutableMapOf<String, Any>(
                "chatTranscript" to newChat.take(60000),
                "sharedFilesInfo" to newFiles.take(20000),
                "voiceRoomActive" to newVoice,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (senderRole in listOf("ADMIN", "TEACHER", "PRINCIPAL")) {
                if (!updatedAnnouncement.isNullOrBlank()) {
                    updates["announcement"] = updatedAnnouncement.trim().take(5000)
                }
                if (!updatedTimetable.isNullOrBlank()) {
                    updates["timetableInfo"] = updatedTimetable.trim().take(3000)
                }
                if (!updatedHomework.isNullOrBlank()) {
                    updates["homeworkInfo"] = updatedHomework.trim().take(3000)
                }
                if (!moderationActionNote.isNullOrBlank()) {
                    val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                    val prevMod = if (portal.moderationLog.isBlank()) "" else "${portal.moderationLog}\n"
                    updates["moderationLog"] = "${prevMod}[$timeStr] $senderName ($senderRole): ${moderationActionNote.trim()}".take(5000)
                }
            }
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adminReviewOrModeratePortal(
        portal: SchoolPortal,
        newStatus: String,
        adminEmail: String,
        moderationReason: String
    ): Result<Unit> = runCatching {
        val docRef = db.collection("school_portals").document(portal.id)
        try {
            val safeStatus = if (newStatus in listOf("APPROVED", "PENDING_APPROVAL", "REJECTED", "SUSPENDED", "REVOKED")) {
                newStatus
            } else {
                "APPROVED"
            }
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val modEntry = "[$timeStr] SuperAdmin ($adminEmail) -> $safeStatus: ${moderationReason.ifBlank { "Official Super Admin Review" }}"
            val updatedMod = if (portal.moderationLog.isBlank()) modEntry else "${portal.moderationLog}\n$modEntry"
            docRef.update(
                mapOf(
                    "portalStatus" to safeStatus,
                    "moderationLog" to updatedMod.take(5000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}
