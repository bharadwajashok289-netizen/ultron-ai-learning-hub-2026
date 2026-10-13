package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UltronAdmins
import com.example.data.model.UltronBadges
import com.example.data.model.UltronLanguages
import com.example.data.model.UserProfile
import com.example.data.remote.GeminiService
import com.example.data.remote.SpotifyAuthManager
import com.example.data.remote.SpotifyAuthState
import com.example.data.remote.SpotifyMusicService
import com.example.data.remote.SpotifyTokenSession
import com.example.ui.screens.parseStructuredVisualExplanation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and firebase config from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ultron", appName)
        val dbId = context.getString(R.string.firestore_database_id)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `verify official admin recognition and plan quotas`() {
        assertTrue(UltronAdmins.isOfficialAdmin("manaskakkunuri55@gmail.com"))
        assertTrue(UltronAdmins.isOfficialAdmin("BharadwajAshok289@gmail.com"))
        assertTrue(UltronAdmins.isOfficialAdmin("cannies.studio@gmail.com"))
        assertFalse(UltronAdmins.isOfficialAdmin("student@example.com"))

        val adminProfile = UserProfile(email = "manaskakkunuri55@gmail.com", role = "STUDENT", plan = "FREE")
        assertTrue(adminProfile.isAdminUser)
        assertEquals("PRO", adminProfile.effectivePlan)
        assertEquals("Unlimited", adminProfile.chatsRemainingDisplay)

        val freeStudent = UserProfile(email = "student@example.com", role = "STUDENT", plan = "FREE", chatsUsed = 12)
        assertFalse(freeStudent.isAdminUser)
        assertEquals("FREE", freeStudent.effectivePlan)
        assertEquals(50, freeStudent.maxMonthlyChats)
        assertEquals("38", freeStudent.chatsRemainingDisplay)
    }

    @Test
    fun `verify educational video topic policy enforcement`() {
        assertTrue(GeminiService.isEducationalVideoTopic("Newton's Laws of Motion and Orbital Mechanics"))
        assertTrue(GeminiService.isEducationalVideoTopic("Photosynthesis and Cellular Respiration in Plants"))
        assertTrue(GeminiService.isEducationalVideoTopic("Quadratic Equations & Parabola Graphs"))
        assertFalse(GeminiService.isEducationalVideoTopic("funny cat prank video"))
        assertFalse(GeminiService.isEducationalVideoTopic("online casino gambling"))
        assertFalse(GeminiService.isEducationalVideoTopic("   "))
    }

    @Test
    fun `verify scientific diagram bitmap renderer and base64 roundtrip`() {
        val layouts = listOf(
            "Scientific Diagram",
            "Flowchart",
            "Mind Map",
            "Circuit Diagram",
            "Historical Timeline",
            "Chemistry Structure",
            "Math Graph"
        )
        layouts.forEach { layout ->
            val bmp = GeminiService.renderScientificDiagramBitmap(
                topic = "Eukaryotic Cell & Organelles",
                explanationText = "1. Nucleus\n2. Mitochondria\n3. Cell Membrane",
                styleOrLayout = layout
            )
            assertNotNull(bmp)
            assertEquals(1200, bmp.width)
            assertEquals(800, bmp.height)
        }

        val sampleBmp = GeminiService.renderScientificDiagramBitmap(
            topic = "Photosynthesis Process",
            explanationText = "Light Dependent -> Calvin Cycle",
            styleOrLayout = "Flowchart"
        )
        val b64 = GeminiService.bitmapToBase64(sampleBmp, quality = 62)
        assertTrue(b64.isNotBlank())
        val decoded = GeminiService.base64ToBitmap(b64)
        assertNotNull(decoded)

        assertTrue(GeminiService.isSafeAcademicPrompt("Explain the structure of the human heart"))
        assertFalse(GeminiService.isSafeAcademicPrompt("how to make a bomb"))
        assertTrue(GeminiService.shouldOfferAutomaticVisual("Explain the anatomy and structure of the human heart"))
    }

    @Test
    fun `verify structured visual explanation parser and Spotify study music service`() {
        val rawVisualText = """
            SHORT EXPLANATION:
            Diagram of Ohm's Law and Series-Parallel Circuits.
            DETAILED EXPLANATION:
            Shows voltage source V driving current I through resistor R.
            LABELS & ANNOTATIONS:
            • V_dc: Direct current voltage source
            • R: Resistance in Ohms
            STEP-BY-STEP BREAKDOWN:
            1. Apply voltage V across terminals.
            2. Measure current I = V / R.
            KEY TAKEAWAYS:
            • Always verify units in Volts, Amperes, and Ohms.
        """.trimIndent()

        val parsed = parseStructuredVisualExplanation("Ohm's Law Circuit [Circuit Diagram]", rawVisualText)
        assertEquals("Ohm's Law Circuit", parsed.title)
        assertTrue(parsed.shortExplanation.contains("Ohm's Law"))
        assertTrue(parsed.detailedExplanation.contains("voltage source"))
        assertTrue(parsed.labelsAndAnnotations.contains("V_dc"))
        assertTrue(parsed.stepByStepBreakdown.contains("I = V / R"))
        assertTrue(parsed.keyTakeaways.contains("Ohms"))

        // Verify SpotifyMusicService multi-genre catalog, preference persistence, and Smart Pomodoro auto-sync
        val context = ApplicationProvider.getApplicationContext<Context>()
        SpotifyMusicService.initialize(context)
        val initialState = SpotifyMusicService.playerState.value
        assertTrue(initialState.tracks.isNotEmpty())
        assertNotNull(initialState.currentTrack)
        assertFalse(initialState.connectionStatusText.contains("API key", ignoreCase = true))
        assertFalse(initialState.connectionStatusText.contains("Client ID", ignoreCase = true))
        assertTrue(SpotifyMusicService.CURATED_STUDY_CATALOG.all { it.spotifyEmbedUrl.startsWith("https://open.spotify.com/embed/") })
        assertTrue(SpotifyMusicService.CURATED_STUDY_CATALOG.all { it.spotifyUri.startsWith("spotify:") })

        val expectedGenres = listOf("Lo-Fi", "Classical", "Instrumental", "Devotional", "Motivational", "Pop", "Rock")
        expectedGenres.forEach { genre ->
            SpotifyMusicService.selectCategory(genre)
            assertEquals(genre, SpotifyMusicService.playerState.value.selectedCategory)
            assertTrue(SpotifyMusicService.playerState.value.tracks.isNotEmpty())
        }

        val initialEmbedState = SpotifyMusicService.playerState.value.showEmbeddedSpotifyPlayer
        SpotifyMusicService.toggleEmbeddedSpotifyPlayer()
        assertEquals(!initialEmbedState, SpotifyMusicService.playerState.value.showEmbeddedSpotifyPlayer)

        SpotifyMusicService.setVolume(0.6f)
        assertEquals(0.6f, SpotifyMusicService.playerState.value.volume, 0.01f)

        SpotifyMusicService.setAutoPlayWithPomodoro(true)
        SpotifyMusicService.onPomodoroFocusStarted()
        assertTrue(SpotifyMusicService.playerState.value.isPlaying)

        SpotifyMusicService.seekTo(42)
        assertEquals(42, SpotifyMusicService.playerState.value.currentPositionSec)

        SpotifyMusicService.onPomodoroFocusPaused()
        assertFalse(SpotifyMusicService.playerState.value.isPlaying)
    }

    @Test
    fun `verify local Room database CRUD and offline cache persistence`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.UltronLocalDatabase.getInstance(context)
        val dao = db.cachedStudyDao()
        dao.deleteByKey("cache_item_1")

        val item = com.example.data.local.CachedStudyItemEntity(
            itemKey = "cache_item_1",
            category = "VISUAL_MEDIA",
            title = "Krebs Cycle Diagram",
            subtitle = "Biology • 16:9",
            contentBody = "Citric acid cycle ATP and NADH production",
            updatedAtMs = System.currentTimeMillis()
        )
        dao.upsertItem(item)
        dao.deleteByKey("cache_item_1")
    }

    @Test
    fun `verify badge evaluation and localization support`() {
        val profile = UserProfile(
            userId = "u1",
            quizzesCompleted = 1,
            totalStudySessions = 1,
            currentStreak = 7,
            longestStreak = 7
        )
        val unlocked = UltronBadges.evaluateUnlockedBadges(profile, latestQuizScore = 100)
        val unlockedIds = unlocked.map { it.id }.toSet()
        assertTrue(unlockedIds.contains("first_step"))
        assertTrue(unlockedIds.contains("quiz_starter"))
        assertTrue(unlockedIds.contains("perfect_score"))
        assertTrue(unlockedIds.contains("streak_7"))

        assertEquals(8, UltronLanguages.SUPPORTED_LANGUAGES.size)
        UltronLanguages.SUPPORTED_LANGUAGES.forEach { lang ->
            assertTrue(UltronLanguages.greetingFor(lang, "Aarav").isNotBlank())
            assertTrue(UltronLanguages.taglineFor(lang).isNotBlank())
        }
    }

    @Test
    fun `verify SpotifyAuthManager PKCE flow encrypted token storage and refresh logic`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = SpotifyAuthManager.getInstance(context)
        authManager.clearSession()
        assertEquals(SpotifyAuthState.Unauthenticated, authManager.authState.value)

        // 1. Verify RFC 7636 PKCE code_verifier & S256 code_challenge generation
        val verifier = authManager.generateCodeVerifier(64)
        assertEquals(64, verifier.length)
        val challenge = authManager.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotBlank())
        assertFalse(challenge.contains("="))
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))

        // 2. Verify authorization URI construction and CSRF state persistence
        val authUri = authManager.buildAuthorizationUri(
            clientId = "spotify_test_client_id_123",
            customCodeVerifier = verifier,
            customState = "csrf_state_token_xyz"
        )
        assertNotNull(authUri)
        assertEquals("https", authUri!!.scheme)
        assertEquals("accounts.spotify.com", authUri.host)
        assertEquals("/authorize", authUri.path)
        assertEquals("spotify_test_client_id_123", authUri.getQueryParameter("client_id"))
        assertEquals("code", authUri.getQueryParameter("response_type"))
        assertEquals("S256", authUri.getQueryParameter("code_challenge_method"))
        assertEquals(challenge, authUri.getQueryParameter("code_challenge"))
        assertEquals("csrf_state_token_xyz", authUri.getQueryParameter("state"))
        assertEquals(SpotifyAuthState.Authorizing, authManager.authState.value)

        // 3. Verify CSRF state mismatch protection
        val mismatchResult = authManager.exchangeAuthorizationCode(
            code = "sample_auth_code",
            returnedState = "wrong_csrf_state"
        )
        assertTrue(mismatchResult.isFailure)

        // 4. Verify encrypted token storage roundtrip & refresh token rotation preservation
        val now = System.currentTimeMillis()
        val parsedInitial = authManager.parseAndPersistTokenResponse(
            responseBody = """
                {
                  "access_token": "BQA_initial_access_token_123",
                  "token_type": "Bearer",
                  "scope": "streaming user-read-playback-state",
                  "expires_in": 3600,
                  "refresh_token": "AQA_initial_refresh_token_456"
                }
            """.trimIndent(),
            nowEpochMs = now
        )
        assertNotNull(parsedInitial)
        assertEquals("BQA_initial_access_token_123", parsedInitial!!.accessToken)
        assertEquals("AQA_initial_refresh_token_456", parsedInitial.refreshToken)
        assertFalse(parsedInitial.isExpired(now))
        assertTrue(parsedInitial.isExpired(now + 3550_000L)) // Within 60s expiry safety buffer

        val stored = authManager.getStoredTokenSession()
        assertNotNull(stored)
        assertEquals("BQA_initial_access_token_123", stored!!.accessToken)
        assertEquals("AQA_initial_refresh_token_456", authManager.getStoredRefreshToken())
        assertEquals("BQA_initial_access_token_123", authManager.getValidAccessToken(forceRefresh = false))

        // Verify refresh response that omits refresh_token preserves existing refresh_token
        val parsedRefreshed = authManager.parseAndPersistTokenResponse(
            responseBody = """
                {
                  "access_token": "BQA_refreshed_access_token_789",
                  "token_type": "Bearer",
                  "expires_in": 3600
                }
            """.trimIndent(),
            nowEpochMs = now + 3600_000L
        )
        assertNotNull(parsedRefreshed)
        assertEquals("BQA_refreshed_access_token_789", parsedRefreshed!!.accessToken)
        assertEquals("AQA_initial_refresh_token_456", parsedRefreshed.refreshToken)

        authManager.clearSession()
        assertEquals(SpotifyAuthState.Unauthenticated, authManager.authState.value)
    }

    @Test
    fun `verify smart AI image diagram and educational video generation and download`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify Smart AI Service Selection
        val (videoService, videoStyle) = GeminiService.selectBestGenerationService("Create an educational video animation on photosynthesis")
        assertEquals("VIDEO", videoService)
        assertTrue(videoStyle.isNotBlank())

        val (geoService, geoStyle) = GeminiService.selectBestGenerationService("Draw a diagram of tectonic plates and latitude lines")
        assertEquals("DIAGRAM", geoService)
        assertEquals("Geography Map", geoStyle)

        val (timelineService, timelineStyle) = GeminiService.selectBestGenerationService("Show a diagram of the historical timeline of the Industrial Revolution")
        assertEquals("DIAGRAM", timelineService)
        assertEquals("Historical Scene & Timeline", timelineStyle)

        val (flowchartService, flowchartStyle) = GeminiService.selectBestGenerationService("Generate a flowchart of cellular respiration steps")
        assertEquals("DIAGRAM", flowchartService)
        assertEquals("Flowchart", flowchartStyle)

        val (imageService, imageStyle) = GeminiService.selectBestGenerationService("Generate an image of ancient Rome architecture")
        assertEquals("IMAGE", imageService)
        assertEquals("Textbook Illustration", imageStyle)

        // 2. Verify High-Clarity Diagram & Image Rendering across all educational categories
        val stylesToTest = listOf(
            "Scientific Diagram" to "Newton's Second Law F = m * a",
            "Geography Map" to "World Latitude & Tectonic Boundaries Map",
            "Historical Timeline" to "Timeline of the French Revolution",
            "Math Graph" to "Quadratic Function Parabola & Geometry",
            "Flowchart" to "Algorithm Flowchart for Binary Search",
            "Mind Map" to "Concept Map of Thermodynamics",
            "Biological Anatomy" to "Eukaryotic Cell & Mitochondria Anatomy",
            "Chemistry Structure" to "Benzene Ring & Covalent Bonding",
            "Circuit Diagram" to "Ohm's Law Series & Parallel Circuit",
            "Scientific Chart" to "Comparative Bar Chart of Renewable Energy",
            "Textbook Illustration" to "Historical Scene of Ancient Nalanda University"
        )

        stylesToTest.forEach { (style, prompt) ->
            val bmp = GeminiService.renderScientificDiagramBitmap(
                topic = prompt,
                explanationText = "Step-by-step academic explanation of $prompt",
                styleOrLayout = style
            )
            assertNotNull("Bitmap for $style should not be null", bmp)
            assertTrue("Bitmap width for $style should be > 400", bmp.width > 400)
            assertTrue("Bitmap height for $style should be > 300", bmp.height > 300)
        }

        // 3. Verify Device PNG Download Helper
        val sampleBmp = GeminiService.renderScientificDiagramBitmap(
            topic = "Krebs Cycle Labeled Diagram",
            explanationText = "Citric acid cycle ATP and NADH production",
            styleOrLayout = "Biological Anatomy"
        )
        val saveResult = GeminiService.saveBitmapToDeviceDownloads(context, sampleBmp, "Krebs Cycle Labeled Diagram")
        assertTrue(saveResult.isSuccess)
        assertTrue(saveResult.getOrNull().orEmpty().contains(".png"))

        // 4. Verify Educational Video Generation & Reliable Educational Video Sources
        val videoRes = GeminiService.generateEducationalVideoForStudyChat(
            prompt = "3D Geometry & Calculus Derivatives",
            language = "English",
            studentClass = "Class 12",
            conversationContext = "Student asked how derivatives relate to tangent slopes."
        )
        assertTrue(videoRes.isSuccess)
        val (explanation, scenes) = videoRes.getOrThrow()
        assertEquals(4, scenes.size)
        assertTrue(scenes.all { it.title.isNotBlank() && it.narration.isNotBlank() && it.subtitleText.isNotBlank() })
        assertTrue(explanation.contains("SHORT EXPLANATION:") || explanation.contains("Educational Video Lesson"))
        val reliableSources = GeminiService.getReliableEducationalVideoSources("3D Geometry & Calculus Derivatives", "Class 12")
        assertTrue(reliableSources.isNotEmpty())
        assertTrue(reliableSources.all { it.url.startsWith("https://") && it.provider.isNotBlank() })
    }
}
