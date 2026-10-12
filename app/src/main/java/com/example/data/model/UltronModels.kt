package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

object UltronAdmins {
    val OFFICIAL_ADMINS = setOf(
        "manaskakkunuri55@gmail.com",
        "bharadwajashok289@gmail.com",
        "cannies.studio@gmail.com"
    )

    fun isOfficialAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return OFFICIAL_ADMINS.contains(email.trim().lowercase())
    }

    const val OFFICIAL_UPI_ID = "shreetulasi@oksbi"
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val studentClass: String = "Class 10",
    val section: String = "A",
    val schoolName: String = "",
    val dob: String = "2009-01-01",
    val age: Int = 16,
    val role: String = "STUDENT", // STUDENT, TEACHER, PRINCIPAL, ADMIN
    val language: String = "English",
    val themePreference: String = "DARK", // DARK, LIGHT, SYSTEM
    val plan: String = "FREE", // FREE, BASIC, PRO
    val subscriptionStatus: String = "ACTIVE", // ACTIVE, EXPIRED
    val subscriptionStartDate: String = "",
    val subscriptionExpiryDate: String = "",
    val chatsUsed: Int = 0,
    val imagesUsed: Int = 0,
    val usageMonth: String = "2026-10",
    val xp: Int = 0,
    val level: Int = 1,
    val currentStreak: Int = 1,
    val longestStreak: Int = 1,
    val lastStudyDate: String = "",
    val unlockedBadges: List<String> = emptyList(),
    val questionsAnswered: Int = 0,
    val quizzesCompleted: Int = 0,
    val flashcardsReviewed: Int = 0,
    val flashcardsMastered: Int = 0,
    val homeworkSessions: Int = 0,
    val focusSessions: Int = 0,
    val studyMinutes: Int = 0,
    val tasksCompleted: Int = 0,
    val totalStudySessions: Int = 0,
    val syllabusTopicsCompleted: Int = 0,
    val onboardingCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isAdminUser: Boolean
        get() = role == "ADMIN" || UltronAdmins.isOfficialAdmin(email)

    val effectivePlan: String
        get() = if (isAdminUser) "PRO" else plan

    val maxMonthlyChats: Int
        get() = when (effectivePlan) {
            "PRO" -> Int.MAX_VALUE
            "BASIC" -> 200
            else -> 50
        }

    val maxMonthlyImages: Int
        get() = when (effectivePlan) {
            "PRO" -> Int.MAX_VALUE
            "BASIC" -> 50
            else -> 10
        }

    val chatsRemainingDisplay: String
        get() = if (effectivePlan == "PRO") "Unlimited" else "${(maxMonthlyChats - chatsUsed).coerceAtLeast(0)}"

    val imagesRemainingDisplay: String
        get() = if (effectivePlan == "PRO") "Unlimited" else "${(maxMonthlyImages - imagesUsed).coerceAtLeast(0)}"

    val xpForNextLevel: Int
        get() = level * 100

    val xpProgressFraction: Float
        get() {
            val currentLevelBase = (level - 1) * 100
            val intoLevel = (xp - currentLevelBase).coerceAtLeast(0)
            return (intoLevel.toFloat() / 100f).coerceIn(0f, 1f)
        }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "fullName" to fullName,
        "email" to email,
        "studentClass" to studentClass,
        "section" to section,
        "schoolName" to schoolName,
        "dob" to dob,
        "age" to age,
        "role" to role,
        "language" to language,
        "themePreference" to themePreference,
        "plan" to plan,
        "subscriptionStatus" to subscriptionStatus,
        "subscriptionStartDate" to subscriptionStartDate,
        "subscriptionExpiryDate" to subscriptionExpiryDate,
        "chatsUsed" to chatsUsed,
        "imagesUsed" to imagesUsed,
        "usageMonth" to usageMonth,
        "xp" to xp,
        "level" to level,
        "currentStreak" to currentStreak,
        "longestStreak" to longestStreak,
        "lastStudyDate" to lastStudyDate,
        "unlockedBadges" to unlockedBadges,
        "questionsAnswered" to questionsAnswered,
        "quizzesCompleted" to quizzesCompleted,
        "flashcardsReviewed" to flashcardsReviewed,
        "flashcardsMastered" to flashcardsMastered,
        "homeworkSessions" to homeworkSessions,
        "focusSessions" to focusSessions,
        "studyMinutes" to studyMinutes,
        "tasksCompleted" to tasksCompleted,
        "totalStudySessions" to totalStudySessions,
        "syllabusTopicsCompleted" to syllabusTopicsCompleted,
        "onboardingCompleted" to onboardingCompleted,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class StudyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "General",
    val deadline: String = "",
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val category: String = "TODAY", // TODAY, TOMORROW, UPCOMING
    val completed: Boolean = false,
    val xpAwarded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Serializable
data class EducationalVideoScene(
    val sceneNumber: Int = 1,
    val title: String = "",
    val formulaOrKeyRule: String = "",
    val narration: String = "",
    val visualType: String = "ORBIT", // ORBIT, WAVE, CIRCUIT, BIOLOGY, GRAPH, HISTORY_TIMELINE, GEOGRAPHY_EARTH, CHEMISTRY_MOLECULE, GEOMETRY_3D
    val subtitleText: String = narration,
    val exampleApplication: String = ""
)

@Serializable
data class ReliableEducationalVideoResource(
    val title: String = "",
    val provider: String = "",
    val subjectCategory: String = "",
    val gradeLevel: String = "Class 6–12",
    val durationLabel: String = "10 min",
    val url: String = "",
    val embedUrl: String = "",
    val description: String = ""
)

@Serializable
data class ChatTurn(
    val role: String, // "user" or "model"
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val modelUsed: String = "gemini-3-flash-preview",
    val mediaType: String = "NONE", // "NONE", "DIAGRAM", "IMAGE", "EDUCATIONAL_VIDEO"
    val mediaPrompt: String = "",
    val mediaBase64: String = "",
    val videoScenes: List<EducationalVideoScene> = emptyList(),
    val reliableVideoSources: List<ReliableEducationalVideoResource> = emptyList()
)

@IgnoreExtraProperties
data class StudyConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val language: String = "English",
    val lastMessagePreview: String = "",
    val messageCount: Int = 0,
    val messagesPayload: String = "[]",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Serializable
data class FlashcardItem(
    val id: String,
    val front: String,
    val back: String,
    val mastered: Boolean = false
)

@IgnoreExtraProperties
data class FlashcardDeck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val subject: String = "Science",
    val totalCards: Int = 1,
    val masteredCards: Int = 0,
    val cardsPayload: String = "[]",
    val xpAwarded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Serializable
data class QuizQuestionItem(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val selectedIndex: Int = -1
)

@IgnoreExtraProperties
data class StudyQuiz(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topic: String = "",
    val difficulty: String = "Medium", // Easy, Medium, Hard
    val totalQuestions: Int = 5,
    val correctAnswers: Int = 0,
    val scorePercent: Int = 0,
    val questionsPayload: String = "[]",
    val completed: Boolean = false,
    val xpAwarded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FocusSessionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val topic: String = "",
    val syllabusSummary: String = "",
    val revisionNotes: String = "",
    val practiceQuestions: String = "",
    val durationMinutes: Int = 25,
    val completed: Boolean = false,
    val xpAwarded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HomeworkSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val extractedQuestion: String = "",
    val stepByStepSolution: String = "",
    val conceptExplanation: String = "",
    val followUpNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val selectedPlan: String = "BASIC", // BASIC, PRO
    val amount: Int = 99,
    val utr: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val processedByAdmin: String = "",
    val adminNote: String = "",
    val notificationStatus: String = "NOTIFIED_DASHBOARD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SharedNotebook(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val title: String = "",
    val subject: String = "",
    val sourceType: String = "WEBSITE", // TEXT, WEBSITE, VIDEO, IMAGE, AUDIO, SCIENCE_LAB
    val sourceUrl: String = "",
    val content: String = "",
    val isPublic: Boolean = true,
    val likesCount: Int = 0,
    val feedbackLog: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolPortal(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorRole: String = "TEACHER", // ADMIN, TEACHER, PRINCIPAL
    val schoolName: String = "",
    val groupName: String = "",
    val description: String = "",
    val announcement: String = "",
    val sharedFilesInfo: String = "",
    val chatTranscript: String = "",
    val voiceRoomActive: Boolean = false,
    val portalStatus: String = "APPROVED", // APPROVED, PENDING_APPROVAL, REJECTED, SUSPENDED, REVOKED
    val academicYear: String = "2026-2027",
    val classesInfo: String = "Class 10, Class 11, Class 12",
    val subjectsInfo: String = "Physics, Mathematics, Chemistry, Biology, CS",
    val timetableInfo: String = "Mon–Fri 08:30 AM – 03:00 PM • STEM Lab: Wed & Fri",
    val homeworkInfo: String = "Complete weekly problem set & review pinned revision notes.",
    val groupCategory: String = "OFFICIAL_PORTAL", // OFFICIAL_PORTAL, CLASS_GROUP, SUBJECT_GROUP, STUDENT_STUDY_GROUP, EXAM_PREP_GROUP
    val moderationLog: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class MediaGenerationJob(
    val id: String = "",
    val jobId: String = id,
    val mediaType: String = "DIAGRAM", // "IMAGE", "DIAGRAM", "VIDEO", "IMAGE_OR_DIAGRAM"
    val prompt: String = "",
    val styleOrLayout: String = "Scientific Blueprint",
    val aspectRatio: String = "16:9",
    val resolutionOrDuration: String = "1K",
    val status: String = "QUEUED", // "QUEUED", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"
    val progressPercent: Int = 0,
    val statusDetail: String = "Queued in Ultron Media Pipeline...",
    val errorMessage: String = "",
    val resultSummary: String = "",
    val resultExplanation: String = "",
    val resultBase64: String = "",
    val resultVideoScenes: List<EducationalVideoScene> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val xpReward: Int,
    val iconName: String
)

object UltronBadges {
    val ALL_BADGES = listOf(
        BadgeDefinition("first_step", "First Step", "Complete your first study activity", 15, "rocket"),
        BadgeDefinition("quiz_starter", "Quiz Starter", "Complete your first MCQ quiz", 25, "quiz"),
        BadgeDefinition("perfect_score", "Perfect Score", "Score 100% on any quiz", 50, "emoji_events"),
        BadgeDefinition("bookworm", "Bookworm", "Complete 10 study sessions", 60, "menu_book"),
        BadgeDefinition("focus_master", "Focus Master", "Complete 10 Focus Mode sessions", 75, "timer"),
        BadgeDefinition("flashcard_pro", "Flashcard Pro", "Master 100 flashcards across decks", 100, "style"),
        BadgeDefinition("task_crusher", "Task Crusher", "Complete 25 study tasks", 80, "task_alt"),
        BadgeDefinition("streak_7", "7-Day Streak", "Maintain a 7-day study streak", 70, "local_fire_department"),
        BadgeDefinition("streak_30", "30-Day Streak", "Maintain a 30-day study streak", 200, "whatshot"),
        BadgeDefinition("syllabus_explorer", "Syllabus Explorer", "Complete your first syllabus scan in Focus Mode", 30, "document_scanner"),
        BadgeDefinition("homework_hero", "Homework Hero", "Complete 10 Homework Assist sessions", 60, "school")
    )

    fun evaluateUnlockedBadges(profile: UserProfile, latestQuizScore: Int = -1): List<BadgeDefinition> {
        val current = profile.unlockedBadges.toSet()
        val newlyUnlocked = mutableListOf<BadgeDefinition>()

        fun check(id: String, condition: Boolean) {
            if (condition && !current.contains(id)) {
                ALL_BADGES.find { it.id == id }?.let { newlyUnlocked.add(it) }
            }
        }

        val anyActivity = profile.totalStudySessions > 0 ||
            profile.quizzesCompleted > 0 ||
            profile.tasksCompleted > 0 ||
            profile.focusSessions > 0 ||
            profile.homeworkSessions > 0 ||
            profile.flashcardsReviewed > 0

        check("first_step", anyActivity)
        check("quiz_starter", profile.quizzesCompleted >= 1)
        check("perfect_score", latestQuizScore == 100)
        check("bookworm", profile.totalStudySessions >= 10)
        check("focus_master", profile.focusSessions >= 10)
        check("flashcard_pro", profile.flashcardsMastered >= 100)
        check("task_crusher", profile.tasksCompleted >= 25)
        check("streak_7", profile.currentStreak >= 7 || profile.longestStreak >= 7)
        check("streak_30", profile.currentStreak >= 30 || profile.longestStreak >= 30)
        check("syllabus_explorer", profile.syllabusTopicsCompleted >= 1)
        check("homework_hero", profile.homeworkSessions >= 10)

        return newlyUnlocked
    }
}

object UltronLanguages {
    val SUPPORTED_LANGUAGES = listOf(
        "English",
        "Telugu",
        "Hindi",
        "Tamil",
        "Kannada",
        "Malayalam",
        "Marathi",
        "Bengali"
    )

    fun greetingFor(language: String, name: String): String {
        val cleanName = name.ifBlank { "Scholar" }
        return when (language) {
            "Telugu" -> "నమస్కారం, $cleanName"
            "Hindi" -> "नमस्ते, $cleanName"
            "Tamil" -> "வணக்கம், $cleanName"
            "Kannada" -> "ನಮಸ್ಕಾರ, $cleanName"
            "Malayalam" -> "നമസ്കാരം, $cleanName"
            "Marathi" -> "नमस्कार, $cleanName"
            "Bengali" -> "নমস্কার, $cleanName"
            else -> "Welcome back, $cleanName"
        }
    }

    fun taglineFor(language: String): String = when (language) {
        "Telugu" -> "తెలివిగా చదవండి. మరింత ముందుకు వెళ్ళండి."
        "Hindi" -> "स्मार्ट तरीके से पढ़ें। आगे बढ़ें।"
        "Tamil" -> "புத்திசாலித்தனமாகப் படியுங்கள். மேலும் முன்னேறுங்கள்."
        "Kannada" -> "ಸ್ಮಾರ್ಟ್ ಆಗಿ ಓದಿ. ಮುಂದೆ ಸಾಗಿ."
        "Malayalam" -> "മിടുക്കോടെ പഠിക്കൂ. മുന്നേറൂ."
        "Marathi" -> "स्मार्ट अभ्यास करा. पुढे जा."
        "Bengali" -> "স্মার্টভাবে পড়ুন। আরও এগিয়ে যান।"
        else -> "Study smarter. Go further."
    }
}

data class MonthlyUpdateInfo(
    val version: String,
    val date: String,
    val title: String,
    val highlights: List<String>
)

object UltronMonthlyUpdates {
    val UPDATES = listOf(
        MonthlyUpdateInfo(
            version = "v2.4 • October 2026",
            date = "October 2026",
            title = "Multimodal Science Lab, Live Voice & Collaborative Notebooks",
            highlights = listOf(
                "Added 1-Click Collaborative Notebooks from web links, videos, and study notes with peer review & workspace chat.",
                "Added School Portals & Educator Group Workspaces with RBAC for Admins, Teachers, and Principals.",
                "Integrated Gemini 3.1 Pro High-Thinking Mode for complex STEM derivations, Search & Maps grounding, and Veo 3.1 educational video generation.",
                "Added Lyria Focus Music generator and Gemini TTS audio playback across study tools."
            )
        ),
        MonthlyUpdateInfo(
            version = "v2.3 • September 2026",
            date = "September 2026",
            title = "Focus Mode Syllabus OCR & 8-Language Localization",
            highlights = listOf(
                "Added multi-page Syllabus OCR scanner with topic breakdown, revision notes, and configurable Pomodoro timer.",
                "Added full support for English, Telugu, Hindi, Tamil, Kannada, Malayalam, Marathi, and Bengali.",
                "Introduced 11 permanent study badges with XP progression and timezone-aware streak calendar."
            )
        )
    )
}
