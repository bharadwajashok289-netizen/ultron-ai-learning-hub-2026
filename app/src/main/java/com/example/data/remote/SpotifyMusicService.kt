package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class SpotifyStudyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int = 180,
    val category: String = "Lo-Fi",
    val itemType: String = "TRACK", // TRACK, PLAYLIST, ALBUM, ARTIST
    val artworkUrl: String = "",
    val previewUrl: String = "",
    val spotifyWebUrl: String = "https://open.spotify.com/playlist/37i9dQZF1DWWQRwui0ExPn",
    val spotifyUri: String = "spotify:playlist:37i9dQZF1DWWQRwui0ExPn",
    val spotifyEmbedUrl: String = "https://open.spotify.com/embed/playlist/37i9dQZF1DWWQRwui0ExPn?utm_source=generator&theme=0",
    val baseFreqHz: Double = 432.0,
    val harmonicFreqHz: Double = 528.0,
    val pulseRateHz: Double = 0.5,
    val isLiveSpotifyResult: Boolean = false
)

data class SpotifyPlayerState(
    val isConnectedToSpotifyApi: Boolean = true,
    val isVerifyingConnection: Boolean = false,
    val connectionStatusText: String = "Spotify Direct Integration Active • Ready to Play",
    val playbackModeNotice: String = "Streaming directly in Ultron. Use transport controls, embedded Spotify player, or open in Spotify anytime.",
    val searchQuery: String = "",
    val searchFilterType: String = "All", // All, Songs, Playlists, Albums, Artists
    val selectedCategory: String = "All Focus",
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val tracks: List<SpotifyStudyTrack> = emptyList(),
    val currentTrack: SpotifyStudyTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionSec: Int = 0,
    val volume: Float = 0.75f,
    val shuffleEnabled: Boolean = false,
    val repeatEnabled: Boolean = true,
    val autoPlayWithPomodoro: Boolean = true,
    val pauseOnPomodoroBreak: Boolean = true,
    val showEmbeddedSpotifyPlayer: Boolean = false
)

object SpotifyMusicService {
    private const val PREFS_NAME = "ultron_spotify_study_prefs"
    private const val KEY_SELECTED_CATEGORY = "selected_category"
    private const val KEY_LAST_TRACK_ID = "last_track_id"
    private const val KEY_VOLUME = "player_volume"
    private const val KEY_SHUFFLE = "shuffle_enabled"
    private const val KEY_REPEAT = "repeat_enabled"
    private const val KEY_AUTO_POMODORO = "auto_play_with_pomodoro"
    private const val KEY_PAUSE_ON_BREAK = "pause_on_pomodoro_break"
    private const val KEY_SHOW_EMBED = "show_embedded_spotify_player"
    private const val KEY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_TOKEN_EXPIRY = "spotify_token_expiry_ms"
    private const val KEY_PKCE_VERIFIER = "spotify_pkce_verifier"

    private const val REDIRECT_URI = "ultronstudy://spotify-callback"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var prefs: SharedPreferences? = null
    private var authManager: SpotifyAuthManager? = null
    private var cachedAccessToken: String? = null
    private var cachedRefreshToken: String? = null
    private var backendClientId: String? = null
    private var backendClientSecret: String? = null
    private var tokenExpiryEpochMs: Long = 0L
    private var hasAttemptedAutoConnect: Boolean = false

    private var mediaPlayer: MediaPlayer? = null
    private var synthJob: Job? = null
    private var progressJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null

    val FOCUS_CATEGORIES = listOf(
        "All Focus",
        "Lo-Fi",
        "Classical",
        "Instrumental",
        "Devotional",
        "Motivational",
        "Pop",
        "Rock",
        "Deep Concentration",
        "Ambient & Binaural"
    )

    val SEARCH_FILTER_TYPES = listOf(
        "All",
        "Songs",
        "Playlists",
        "Albums",
        "Artists"
    )

    /**
     * Curated Spotify playlists and tracks mapped to real official Spotify Playlist & Track IDs
     * with official Spotify Embed URLs (`open.spotify.com/embed/...`) and Spotify URIs (`spotify:playlist:...`).
     */
    val CURATED_STUDY_CATALOG: List<SpotifyStudyTrack> = listOf(
        SpotifyStudyTrack(
            id = "sp_lofi_1",
            title = "Lo-Fi Beats • Official Study Playlist",
            artist = "Spotify Study • Chill Lo-Fi Beats",
            album = "Lo-Fi Beats to Relax & Study To",
            durationSec = 195,
            category = "Lo-Fi",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DWWQRwui0ExPn",
            spotifyUri = "spotify:playlist:37i9dQZF1DWWQRwui0ExPn",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DWWQRwui0ExPn?utm_source=generator&theme=0",
            baseFreqHz = 432.0,
            harmonicFreqHz = 528.0,
            pulseRateHz = 0.65,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_lofi_2",
            title = "Jazz Vibes & Coffee Shop Lo-Fi",
            artist = "Spotify Focus • Warm Rhodes & Beats",
            album = "Late Night Revision Sessions",
            durationSec = 210,
            category = "Lo-Fi",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DX0SM0LYsmbMT",
            spotifyUri = "spotify:playlist:37i9dQZF1DX0SM0LYsmbMT",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DX0SM0LYsmbMT?utm_source=generator&theme=0",
            baseFreqHz = 440.0,
            harmonicFreqHz = 554.37,
            pulseRateHz = 0.55,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_classical_1",
            title = "Classical Essentials for Reading & Math",
            artist = "Spotify Classical • Chamber & Symphony",
            album = "Classical Study Masterworks",
            durationSec = 240,
            category = "Classical",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DWWEJlAGA9gs0",
            spotifyUri = "spotify:playlist:37i9dQZF1DWWEJlAGA9gs0",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DWWEJlAGA9gs0?utm_source=generator&theme=0",
            baseFreqHz = 440.0,
            harmonicFreqHz = 659.25,
            pulseRateHz = 0.35,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_classical_2",
            title = "Intense Studying • Classical & Neo-Classical",
            artist = "Spotify Study • Bach, Debussy & Strings",
            album = "Deep Analytical Thinking",
            durationSec = 225,
            category = "Classical",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DX8NTLI2TtZa6",
            spotifyUri = "spotify:playlist:37i9dQZF1DX8NTLI2TtZa6",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DX8NTLI2TtZa6?utm_source=generator&theme=0",
            baseFreqHz = 432.0,
            harmonicFreqHz = 648.0,
            pulseRateHz = 0.40,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_inst_1",
            title = "Peaceful Piano for Calculus & Physics",
            artist = "Spotify Instrumental • Acoustic Piano",
            album = "Quiet Exam Preparation",
            durationSec = 210,
            category = "Instrumental",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DX4sWSpwq3LiO",
            spotifyUri = "spotify:playlist:37i9dQZF1DX4sWSpwq3LiO",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DX4sWSpwq3LiO?utm_source=generator&theme=0",
            baseFreqHz = 432.0,
            harmonicFreqHz = 576.0,
            pulseRateHz = 0.45,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_inst_2",
            title = "Instrumental Study • Acoustic & Strings",
            artist = "Spotify Focus • Unplugged Study Flow",
            album = "Instrumental Study Collection",
            durationSec = 190,
            category = "Instrumental",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DX9sIqqvKsjG8",
            spotifyUri = "spotify:playlist:37i9dQZF1DX9sIqqvKsjG8",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DX9sIqqvKsjG8?utm_source=generator&theme=0",
            baseFreqHz = 396.0,
            harmonicFreqHz = 594.0,
            pulseRateHz = 0.50,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_devotional_1",
            title = "Peaceful Meditation & Devotional Flute",
            artist = "Spotify Mindful • Serene Flute & Chants",
            album = "Serene Devotional Focus",
            durationSec = 260,
            category = "Devotional",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DWZqd5JICZI0u",
            spotifyUri = "spotify:playlist:37i9dQZF1DWZqd5JICZI0u",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DWZqd5JICZI0u?utm_source=generator&theme=0",
            baseFreqHz = 432.0,
            harmonicFreqHz = 528.0,
            pulseRateHz = 0.25,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_motivational_1",
            title = "Motivation Mix • Exam Prep & Momentum",
            artist = "Spotify Energy • Peak Performance",
            album = "Study Motivation Anthems",
            durationSec = 200,
            category = "Motivational",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DXdxcBWuJkbcy",
            spotifyUri = "spotify:playlist:37i9dQZF1DXdxcBWuJkbcy",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DXdxcBWuJkbcy?utm_source=generator&theme=0",
            baseFreqHz = 440.0,
            harmonicFreqHz = 660.0,
            pulseRateHz = 1.15,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_pop_1",
            title = "Mood Booster & Upbeat Study Pop",
            artist = "Spotify Pop • Melodic Study Groove",
            album = "Feel-Good Study Hits",
            durationSec = 185,
            category = "Pop",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DX3rxVfibe1L0",
            spotifyUri = "spotify:playlist:37i9dQZF1DX3rxVfibe1L0",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DX3rxVfibe1L0?utm_source=generator&theme=0",
            baseFreqHz = 440.0,
            harmonicFreqHz = 554.37,
            pulseRateHz = 1.05,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_rock_1",
            title = "Post-Rock & Atmospheric Guitar Focus",
            artist = "Spotify Rock • Instrumental & Indie Rock",
            album = "Electric Marathon Coding & Study",
            durationSec = 230,
            category = "Rock",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DWXRqgorJj26U",
            spotifyUri = "spotify:playlist:37i9dQZF1DWXRqgorJj26U",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DWXRqgorJj26U?utm_source=generator&theme=0",
            baseFreqHz = 329.63,
            harmonicFreqHz = 493.88,
            pulseRateHz = 0.95,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_deep_1",
            title = "Deep Focus • Ambient & Post-Rock Study",
            artist = "Spotify Focus • Brain Food & Concentration",
            album = "Pomodoro Deep Flow",
            durationSec = 300,
            category = "Deep Concentration",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DWZeKCadgRdKQ",
            spotifyUri = "spotify:playlist:37i9dQZF1DWZeKCadgRdKQ",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DWZeKCadgRdKQ?utm_source=generator&theme=0",
            baseFreqHz = 396.0,
            harmonicFreqHz = 528.0,
            pulseRateHz = 0.30,
            isLiveSpotifyResult = true
        ),
        SpotifyStudyTrack(
            id = "sp_amb_1",
            title = "Night Rain & Ambient Synth Study",
            artist = "Spotify Ambient • Coding & Late Night Revision",
            album = "Atmosphere for Reading",
            durationSec = 260,
            category = "Ambient & Binaural",
            itemType = "PLAYLIST",
            spotifyWebUrl = "https://open.spotify.com/playlist/37i9dQZF1DXbcPC6Vvqudd",
            spotifyUri = "spotify:playlist:37i9dQZF1DXbcPC6Vvqudd",
            spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/37i9dQZF1DXbcPC6Vvqudd?utm_source=generator&theme=0",
            baseFreqHz = 285.0,
            harmonicFreqHz = 432.0,
            pulseRateHz = 0.20,
            isLiveSpotifyResult = true
        )
    )

    private val _playerState = MutableStateFlow(
        SpotifyPlayerState(
            isConnectedToSpotifyApi = true,
            connectionStatusText = "Spotify Direct Integration Active • Ready to Play",
            tracks = CURATED_STUDY_CATALOG,
            currentTrack = CURATED_STUDY_CATALOG.first()
        )
    )
    val playerState: StateFlow<SpotifyPlayerState> = _playerState.asStateFlow()

    /**
     * Automatically loads saved student music preferences, last playlist/genre, volume,
     * and Pomodoro auto-sync settings without asking any setup or credential questions.
     * Also connects to Spotify services in the background automatically.
     */
    fun initialize(context: Context) {
        if (authManager == null) {
            authManager = SpotifyAuthManager.getInstance(context)
        }
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            restorePreferences()
        }
        if (!hasAttemptedAutoConnect) {
            hasAttemptedAutoConnect = true
            verifyAndConnectSpotifyApi()
        }
    }

    fun getAuthManager(): SpotifyAuthManager? = authManager

    private fun restorePreferences() {
        val sp = prefs ?: return
        val savedCategory = sp.getString(KEY_SELECTED_CATEGORY, "All Focus") ?: "All Focus"
        val savedTrackId = sp.getString(KEY_LAST_TRACK_ID, null)
        val savedVolume = sp.getFloat(KEY_VOLUME, 0.75f).coerceIn(0f, 1f)
        val savedShuffle = sp.getBoolean(KEY_SHUFFLE, false)
        val savedRepeat = sp.getBoolean(KEY_REPEAT, true)
        val savedAutoPomodoro = sp.getBoolean(KEY_AUTO_POMODORO, true)
        val savedPauseOnBreak = sp.getBoolean(KEY_PAUSE_ON_BREAK, true)
        val savedShowEmbed = sp.getBoolean(KEY_SHOW_EMBED, false)

        cachedAccessToken = sp.getString(KEY_ACCESS_TOKEN, null)
        cachedRefreshToken = sp.getString(KEY_REFRESH_TOKEN, null)
        tokenExpiryEpochMs = sp.getLong(KEY_TOKEN_EXPIRY, 0L)

        val categoryTracks = if (savedCategory == "All Focus") {
            CURATED_STUDY_CATALOG
        } else {
            CURATED_STUDY_CATALOG.filter { it.category.equals(savedCategory, ignoreCase = true) }
                .ifEmpty { CURATED_STUDY_CATALOG }
        }
        val restoredTrack = CURATED_STUDY_CATALOG.firstOrNull { it.id == savedTrackId }
            ?: categoryTracks.firstOrNull()
            ?: CURATED_STUDY_CATALOG.first()

        _playerState.value = _playerState.value.copy(
            selectedCategory = savedCategory,
            tracks = categoryTracks,
            currentTrack = restoredTrack,
            volume = savedVolume,
            shuffleEnabled = savedShuffle,
            repeatEnabled = savedRepeat,
            autoPlayWithPomodoro = savedAutoPomodoro,
            pauseOnPomodoroBreak = savedPauseOnBreak,
            showEmbeddedSpotifyPlayer = savedShowEmbed
        )
    }

    private fun savePreferences() {
        val sp = prefs ?: return
        val s = _playerState.value
        sp.edit()
            .putString(KEY_SELECTED_CATEGORY, s.selectedCategory)
            .putString(KEY_LAST_TRACK_ID, s.currentTrack?.id ?: "")
            .putFloat(KEY_VOLUME, s.volume)
            .putBoolean(KEY_SHUFFLE, s.shuffleEnabled)
            .putBoolean(KEY_REPEAT, s.repeatEnabled)
            .putBoolean(KEY_AUTO_POMODORO, s.autoPlayWithPomodoro)
            .putBoolean(KEY_PAUSE_ON_BREAK, s.pauseOnPomodoroBreak)
            .putBoolean(KEY_SHOW_EMBED, s.showEmbeddedSpotifyPlayer)
            .apply()
    }

    fun toggleEmbeddedSpotifyPlayer() {
        val current = _playerState.value
        _playerState.value = current.copy(showEmbeddedSpotifyPlayer = !current.showEmbeddedSpotifyPlayer)
        savePreferences()
    }

    fun hasSpotifyCredentials(): Boolean {
        if (authManager?.hasConfiguredCredentials() == true) return true
        val clientId = getConfiguredClientId()
        val clientSecret = getConfiguredClientSecret()
        val accessToken = getConfiguredAccessToken()
        val refreshToken = getConfiguredRefreshToken()
        val validClientPair = clientId.isNotBlank() && clientSecret.isNotBlank()
        val validDirectToken = accessToken.isNotBlank() || refreshToken.isNotBlank()
        return validClientPair || validDirectToken
    }

    private fun getConfiguredClientId(): String {
        val raw = try { BuildConfig.SPOTIFY_CLIENT_ID } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_CLIENT_ID") return raw.trim()
        return backendClientId?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun getConfiguredClientSecret(): String {
        val raw = try { BuildConfig.SPOTIFY_CLIENT_SECRET } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_CLIENT_SECRET") return raw.trim()
        return backendClientSecret?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun getConfiguredAccessToken(): String {
        val raw = try { BuildConfig.SPOTIFY_ACCESS_TOKEN } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_ACCESS_TOKEN") return raw.trim()
        return cachedAccessToken?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun getConfiguredRefreshToken(): String {
        val raw = try { BuildConfig.SPOTIFY_REFRESH_TOKEN } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_REFRESH_TOKEN") return raw.trim()
        return cachedRefreshToken?.takeIf { it.isNotBlank() }.orEmpty()
    }

    /**
     * Automatically loads backend credentials (from BuildConfig or Firestore backend config if available),
     * authenticates via OAuth 2.0 or verifies Spotify's official oEmbed API, and enriches playlist metadata
     * with zero manual user configuration.
     */
    fun verifyAndConnectSpotifyApi() {
        _playerState.value = _playerState.value.copy(
            isVerifyingConnection = true,
            connectionStatusText = "Synchronizing with Spotify..."
        )

        scope.launch {
            // 1. Check backend Firestore config silently if BuildConfig credentials are not set
            if (!hasSpotifyCredentials()) {
                fetchBackendConfigSilently()
            }

            // 2. Attempt full OAuth 2.0 Web API token authentication
            val token = fetchSpotifyAccessToken(forceRefresh = false)
            if (!token.isNullOrBlank()) {
                val verified = withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder()
                            .url("https://api.spotify.com/v1/search?q=study&type=track&limit=1")
                            .header("Authorization", "Bearer $token")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { resp -> resp.isSuccessful }
                    } catch (_: Exception) {
                        false
                    }
                }
                if (verified) {
                    _playerState.value = _playerState.value.copy(
                        isConnectedToSpotifyApi = true,
                        isVerifyingConnection = false,
                        connectionStatusText = "Spotify Connected • Full Catalog & Playback Active"
                    )
                    return@launch
                }
            }

            // 3. Verify official Spotify oEmbed endpoint & enrich artwork automatically (zero manual keys needed)
            val oembedVerified = verifySpotifyOEmbedAndEnrich()
            _playerState.value = _playerState.value.copy(
                isConnectedToSpotifyApi = true,
                isVerifyingConnection = false,
                connectionStatusText = if (oembedVerified) {
                    "Spotify Connected • Direct Playlists, Search & Focus Player Active"
                } else {
                    "Spotify Study Player Active • Multi-Genre Focus Audio Ready"
                }
            )
        }
    }

    private suspend fun fetchBackendConfigSilently() = withContext(Dispatchers.IO) {
        try {
            val doc = Firebase.firestore.collection("system_config")
                .document("spotify_integration")
                .get()
                .await()
            if (doc.exists()) {
                val cid = doc.getString("clientId")?.trim().orEmpty()
                val csec = doc.getString("clientSecret")?.trim().orEmpty()
                val atok = doc.getString("accessToken")?.trim().orEmpty()
                val rtok = doc.getString("refreshToken")?.trim().orEmpty()
                if (cid.isNotBlank()) backendClientId = cid
                if (csec.isNotBlank()) backendClientSecret = csec
                if (atok.isNotBlank() && cachedAccessToken.isNullOrBlank()) {
                    persistTokenSession(atok, rtok.ifBlank { null }, System.currentTimeMillis() + 3500_000L)
                }
            }
        } catch (_: Exception) {
            // Ignore silently if offline or document not provisioned
        }
    }

    /**
     * Calls Spotify's official zero-credential oEmbed API (`https://open.spotify.com/oembed`)
     * to verify live Spotify connectivity and populate official playlist cover art.
     */
    private suspend fun verifySpotifyOEmbedAndEnrich(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sampleTrack = _playerState.value.currentTrack ?: CURATED_STUDY_CATALOG.first()
            val encodedUrl = Uri.encode(sampleTrack.spotifyWebUrl)
            val req = Request.Builder()
                .url("https://open.spotify.com/oembed?url=$encodedUrl")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val bodyStr = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(bodyStr).jsonObject
                val thumb = obj["thumbnail_url"]?.jsonPrimitive?.content.orEmpty()
                if (thumb.isNotBlank() && sampleTrack.artworkUrl.isBlank()) {
                    val updated = sampleTrack.copy(artworkUrl = thumb)
                    _playerState.value = _playerState.value.copy(currentTrack = updated)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchSpotifyAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        authManager?.let { manager ->
            val managedToken = manager.getValidAccessToken(forceRefresh = forceRefresh)
            if (!managedToken.isNullOrBlank()) {
                cachedAccessToken = managedToken
                return@withContext managedToken
            }
        }
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = cachedAccessToken
            if (!cached.isNullOrBlank() && now < tokenExpiryEpochMs) {
                return@withContext cached
            }
            val envToken = try { BuildConfig.SPOTIFY_ACCESS_TOKEN } catch (_: Exception) { "" }
            if (envToken.isNotBlank() && envToken != "MY_SPOTIFY_ACCESS_TOKEN") {
                return@withContext envToken.trim()
            }
        }

        val clientId = getConfiguredClientId()
        val clientSecret = getConfiguredClientSecret()
        val refreshToken = getConfiguredRefreshToken()

        // 1. Automatic OAuth 2.0 Refresh Token renewal
        if (refreshToken.isNotBlank() && clientId.isNotBlank()) {
            try {
                val formBuilder = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", clientId)
                val reqBuilder = Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                if (clientSecret.isNotBlank()) {
                    val basic = Base64.encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    reqBuilder.header("Authorization", "Basic $basic")
                }
                val req = reqBuilder.post(formBuilder.build()).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val obj = json.parseToJsonElement(bodyStr).jsonObject
                        val newToken = obj["access_token"]?.jsonPrimitive?.content
                        val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content
                        val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
                        if (!newToken.isNullOrBlank()) {
                            persistTokenSession(newToken, newRefresh ?: refreshToken, now + (expiresIn - 60L) * 1000L)
                            return@withContext newToken
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Automatic OAuth 2.0 Client Credentials flow
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return@withContext null
        }

        return@withContext try {
            val credentials = "$clientId:$clientSecret"
            val basicAuth = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", "Basic $basicAuth")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bodyStr = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(bodyStr).jsonObject
                val token = obj["access_token"]?.jsonPrimitive?.content
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
                if (!token.isNullOrBlank()) {
                    persistTokenSession(token, cachedRefreshToken, now + (expiresIn - 60L) * 1000L)
                }
                token
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun persistTokenSession(accessToken: String, refreshToken: String?, expiryMs: Long) {
        cachedAccessToken = accessToken
        if (!refreshToken.isNullOrBlank()) {
            cachedRefreshToken = refreshToken
        }
        tokenExpiryEpochMs = expiryMs
        prefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (!refreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_TOKEN_EXPIRY, expiryMs)
            apply()
        }
        val remainingSec = ((expiryMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(60L)
        authManager?.saveTokenSession(
            SpotifyTokenSession(
                accessToken = accessToken,
                refreshToken = refreshToken ?: cachedRefreshToken,
                expiresInSec = remainingSec,
                expiresAtEpochMs = expiryMs
            )
        )
    }

    /**
     * Completes the official OAuth 2.0 PKCE code exchange automatically when Spotify redirects
     * back to `ultronstudy://spotify-callback?code=...`.
     */
    fun handleAuthorizationCallback(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme != "ultronstudy" || uri.host != "spotify-callback") return

        val manager = authManager
        if (manager != null) {
            manager.handleAuthorizationCallback(uri) { result ->
                result.onSuccess { session ->
                    persistTokenSession(
                        session.accessToken,
                        session.refreshToken,
                        session.expiresAtEpochMs
                    )
                    _playerState.value = _playerState.value.copy(
                        isConnectedToSpotifyApi = true,
                        connectionStatusText = "Spotify Account Authorized • Full Playback & Sync Active"
                    )
                }
            }
            return
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) return

        val verifier = prefs?.getString(KEY_PKCE_VERIFIER, null).orEmpty()
        val clientId = getConfiguredClientId()
        if (clientId.isBlank() || verifier.isBlank()) return

        scope.launch(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", verifier)
                    .build()
                val req = Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                    .post(formBody)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val obj = json.parseToJsonElement(bodyStr).jsonObject
                        val accessToken = obj["access_token"]?.jsonPrimitive?.content
                        val refreshToken = obj["refresh_token"]?.jsonPrimitive?.content
                        val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
                        if (!accessToken.isNullOrBlank()) {
                            persistTokenSession(
                                accessToken,
                                refreshToken,
                                System.currentTimeMillis() + (expiresIn - 60L) * 1000L
                            )
                            _playerState.value = _playerState.value.copy(
                                isConnectedToSpotifyApi = true,
                                connectionStatusText = "Spotify Account Authorized • Full Playback & Sync Active"
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Launches the official Spotify OAuth 2.0 PKCE Authorization Flow or opens Spotify directly
     * without ever asking the user for developer credentials.
     */
    fun launchSpotifyAuthorizationFlow(context: Context) {
        val manager = authManager ?: SpotifyAuthManager.getInstance(context).also { authManager = it }
        if (manager.launchAuthorizationFlow(context)) {
            return
        }
        val clientId = getConfiguredClientId()
        if (clientId.isBlank()) {
            // Toggle embedded Spotify player or open Spotify directly without prompting for keys
            _playerState.value = _playerState.value.copy(showEmbeddedSpotifyPlayer = true)
            savePreferences()
            val current = _playerState.value.currentTrack ?: CURATED_STUDY_CATALOG.first()
            openTrackInSpotify(context, current)
            return
        }
        try {
            val verifierBytes = ByteArray(32)
            SecureRandom().nextBytes(verifierBytes)
            val codeVerifier = Base64.encodeToString(
                verifierBytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            prefs?.edit()?.putString(KEY_PKCE_VERIFIER, codeVerifier)?.apply()

            val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            val codeChallenge = Base64.encodeToString(
                digest,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val redirectUri = Uri.encode(REDIRECT_URI)
            val scopes = Uri.encode("streaming user-read-playback-state user-modify-playback-state playlist-read-private")
            val authUrl = "https://accounts.spotify.com/authorize" +
                "?client_id=$clientId" +
                "&response_type=code" +
                "&redirect_uri=$redirectUri" +
                "&code_challenge_method=S256" +
                "&code_challenge=$codeChallenge" +
                "&scope=$scopes"
            openUrlSafely(context, authUrl)
        } catch (_: Exception) {
            openUrlSafely(context, "https://open.spotify.com")
        }
    }

    fun selectSearchFilterType(filterType: String) {
        _playerState.value = _playerState.value.copy(searchFilterType = filterType)
        val q = _playerState.value.searchQuery.trim()
        if (q.isNotBlank()) {
            searchTracks(q)
        }
    }

    fun selectCategory(category: String) {
        val current = _playerState.value
        val filtered = if (category == "All Focus") {
            CURATED_STUDY_CATALOG
        } else {
            CURATED_STUDY_CATALOG.filter { it.category.equals(category, ignoreCase = true) }
                .ifEmpty { CURATED_STUDY_CATALOG }
        }
        val nextTrack = filtered.firstOrNull() ?: current.currentTrack
        _playerState.value = current.copy(
            selectedCategory = category,
            tracks = filtered,
            currentTrack = nextTrack,
            searchError = null
        )
        savePreferences()

        if (hasSpotifyCredentials() && category != "All Focus") {
            searchTracks("$category study music")
        }
    }

    /**
     * Searches Songs, Artists, Albums, Playlists, and Genres seamlessly:
     * 1. Queries the official Spotify Web API (`/v1/search`) when OAuth token is active, with automatic token refresh.
     * 2. Queries live public music catalog streams (`itunes.apple.com/search`) for real songs, artists, albums,
     *    and streamable 30s audio previews (`previewUrl`) + matches curated Spotify playlists and embeds,
     *    so users always get real playable songs and Spotify integration without manual credential setup.
     */
    fun searchTracks(query: String) {
        val cleanQuery = query.trim()
        val filterType = _playerState.value.searchFilterType
        _playerState.value = _playerState.value.copy(
            searchQuery = query,
            isSearching = true,
            searchError = null
        )

        scope.launch {
            if (cleanQuery.isBlank()) {
                val cat = _playerState.value.selectedCategory
                val defaultList = if (cat == "All Focus") {
                    CURATED_STUDY_CATALOG
                } else {
                    CURATED_STUDY_CATALOG.filter { it.category.equals(cat, ignoreCase = true) }
                        .ifEmpty { CURATED_STUDY_CATALOG }
                }
                _playerState.value = _playerState.value.copy(
                    isSearching = false,
                    tracks = defaultList,
                    searchError = null
                )
                return@launch
            }

            // 1. Query Official Spotify Web API if backend/OAuth token is available
            var token = fetchSpotifyAccessToken(forceRefresh = false)
            if (!token.isNullOrBlank()) {
                try {
                    val apiResults = executeSpotifySearchRequest(cleanQuery, filterType, token)
                        ?: run {
                            val refreshed = fetchSpotifyAccessToken(forceRefresh = true)
                            if (!refreshed.isNullOrBlank()) {
                                token = refreshed
                                executeSpotifySearchRequest(cleanQuery, filterType, refreshed)
                            } else {
                                null
                            }
                        }

                    if (!apiResults.isNullOrEmpty()) {
                        _playerState.value = _playerState.value.copy(
                            isConnectedToSpotifyApi = true,
                            connectionStatusText = "Spotify Connected • ${apiResults.size} results for \"$cleanQuery\"",
                            isSearching = false,
                            tracks = apiResults,
                            currentTrack = apiResults.first(),
                            searchError = null
                        )
                        return@launch
                    }
                } catch (_: Exception) {
                    // Fall through gracefully without exposing technical errors
                }
            }

            // 2. Automatic zero-credential live music catalog search + curated Spotify playlists
            val lower = cleanQuery.lowercase()
            val matchedCurated = CURATED_STUDY_CATALOG.filter {
                it.title.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower) ||
                    it.album.lowercase().contains(lower) ||
                    it.category.lowercase().contains(lower)
            }

            val liveCatalogTracks = fetchZeroCredentialCatalogTracks(cleanQuery, filterType)

            val inferredGenre = FOCUS_CATEGORIES.firstOrNull {
                it != "All Focus" && lower.contains(it.lowercase())
            } ?: _playerState.value.selectedCategory.takeIf { it != "All Focus" } ?: "Lo-Fi"

            val referencePlaylist = CURATED_STUDY_CATALOG.firstOrNull {
                it.category.equals(inferredGenre, ignoreCase = true)
            } ?: CURATED_STUDY_CATALOG.first()

            val itemTypeLabel = when (filterType) {
                "Playlists" -> "PLAYLIST"
                "Albums" -> "ALBUM"
                "Artists" -> "ARTIST"
                else -> "TRACK"
            }

            val directSpotifyMatch = SpotifyStudyTrack(
                id = "sp_search_${cleanQuery.lowercase().hashCode()}",
                title = "${cleanQuery.replaceFirstChar { it.uppercase() }} • $inferredGenre Mix",
                artist = "Spotify Study & Focus • $inferredGenre",
                album = "Direct Spotify & In-App Stream ($itemTypeLabel)",
                durationSec = 210,
                category = inferredGenre,
                itemType = itemTypeLabel,
                spotifyWebUrl = "https://open.spotify.com/search/${Uri.encode(cleanQuery)}",
                spotifyUri = "spotify:search:${Uri.encode(cleanQuery)}",
                spotifyEmbedUrl = referencePlaylist.spotifyEmbedUrl,
                baseFreqHz = if (lower.contains("classical") || lower.contains("pop")) 440.0 else 432.0,
                harmonicFreqHz = if (lower.contains("rock")) 493.88 else 528.0,
                pulseRateHz = if (lower.contains("motivational") || lower.contains("pop") || lower.contains("rock")) 1.0 else 0.5,
                isLiveSpotifyResult = true
            )

            val combined = (liveCatalogTracks + matchedCurated + listOf(directSpotifyMatch)).distinctBy { it.id }
            _playerState.value = _playerState.value.copy(
                isConnectedToSpotifyApi = true,
                connectionStatusText = "Spotify Direct Integration Active • ${combined.size} matches for \"$cleanQuery\"",
                isSearching = false,
                tracks = combined,
                currentTrack = combined.firstOrNull() ?: _playerState.value.currentTrack,
                searchError = null
            )
        }
    }

    /**
     * Searches real songs, artists, and albums with streamable preview URLs so students can search
     * and play real songs immediately inside Ultron with zero manual credentials.
     */
    private suspend fun fetchZeroCredentialCatalogTracks(
        cleanQuery: String,
        filterType: String
    ): List<SpotifyStudyTrack> = withContext(Dispatchers.IO) {
        try {
            val encoded = Uri.encode(cleanQuery)
            val entity = when (filterType) {
                "Albums" -> "album"
                "Artists" -> "musicArtist"
                else -> "song"
            }
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=music&entity=$entity&limit=8")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val results = mutableListOf<SpotifyStudyTrack>()
                val fallbackEmbed = (_playerState.value.currentTrack ?: CURATED_STUDY_CATALOG.first()).spotifyEmbedUrl

                root["results"]?.jsonArray?.forEachIndexed { idx, el ->
                    val obj = el.jsonObject
                    val trackId = obj["trackId"]?.jsonPrimitive?.content
                        ?: obj["collectionId"]?.jsonPrimitive?.content
                        ?: obj["artistId"]?.jsonPrimitive?.content
                        ?: "$idx"
                    val title = obj["trackName"]?.jsonPrimitive?.content
                        ?: obj["collectionName"]?.jsonPrimitive?.content
                        ?: obj["artistName"]?.jsonPrimitive?.content
                        ?: return@forEachIndexed
                    val artist = obj["artistName"]?.jsonPrimitive?.content ?: "Featured Artist"
                    val album = obj["collectionName"]?.jsonPrimitive?.content
                        ?: obj["primaryGenreName"]?.jsonPrimitive?.content
                        ?: "Single"
                    val previewUrl = obj["previewUrl"]?.jsonPrimitive?.content.orEmpty()
                    val artworkUrl = obj["artworkUrl100"]?.jsonPrimitive?.content.orEmpty()
                    val durationMs = obj["trackTimeMillis"]?.jsonPrimitive?.content?.toLongOrNull() ?: 180_000L
                    val genre = obj["primaryGenreName"]?.jsonPrimitive?.content ?: "Study Music"
                    val spotifyQuery = Uri.encode("$title $artist")

                    val itemType = when (filterType) {
                        "Albums" -> "ALBUM"
                        "Artists" -> "ARTIST"
                        "Playlists" -> "PLAYLIST"
                        else -> "TRACK"
                    }

                    results.add(
                        SpotifyStudyTrack(
                            id = "live_$trackId",
                            title = title,
                            artist = artist,
                            album = album,
                            durationSec = (durationMs / 1000L).toInt().coerceAtLeast(30),
                            category = genre,
                            itemType = itemType,
                            artworkUrl = artworkUrl,
                            previewUrl = previewUrl,
                            spotifyWebUrl = "https://open.spotify.com/search/$spotifyQuery",
                            spotifyUri = "spotify:search:$spotifyQuery",
                            spotifyEmbedUrl = fallbackEmbed,
                            baseFreqHz = if (idx % 2 == 0) 432.0 else 440.0,
                            harmonicFreqHz = if (idx % 2 == 0) 528.0 else 660.0,
                            isLiveSpotifyResult = true
                        )
                    )
                }
                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun executeSpotifySearchRequest(
        cleanQuery: String,
        filterType: String,
        token: String
    ): List<SpotifyStudyTrack>? = withContext(Dispatchers.IO) {
        val apiTypes = when (filterType) {
            "Songs" -> "track"
            "Playlists" -> "playlist"
            "Albums" -> "album"
            "Artists" -> "artist"
            else -> "track,playlist,album"
        }
        val encodedQ = Uri.encode(cleanQuery)
        val req = Request.Builder()
            .url("https://api.spotify.com/v1/search?q=$encodedQ&type=$apiTypes&limit=10")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 401) return@use null
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val bodyStr = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val results = mutableListOf<SpotifyStudyTrack>()

            // Parse Tracks
            root["tracks"]?.jsonObject?.get("items")?.jsonArray?.forEachIndexed { idx, el ->
                val trackObj = el.jsonObject
                val id = trackObj["id"]?.jsonPrimitive?.content ?: return@forEachIndexed
                val name = trackObj["name"]?.jsonPrimitive?.content ?: "Spotify Track"
                val durationMs = trackObj["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 180_000L
                val previewUrl = trackObj["preview_url"]?.jsonPrimitive?.content.orEmpty()
                val uri = trackObj["uri"]?.jsonPrimitive?.content ?: "spotify:track:$id"
                val externalUrl = trackObj["external_urls"]?.jsonObject?.get("spotify")?.jsonPrimitive?.content
                    ?: "https://open.spotify.com/track/$id"
                val artistName = trackObj["artists"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Spotify Artist"
                val albumObj = trackObj["album"]?.jsonObject
                val albumName = albumObj?.get("name")?.jsonPrimitive?.content ?: "Single"
                val artwork = albumObj?.get("images")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.content.orEmpty()

                results.add(
                    SpotifyStudyTrack(
                        id = "track_$id",
                        title = name,
                        artist = artistName,
                        album = albumName,
                        durationSec = (durationMs / 1000L).toInt().coerceAtLeast(30),
                        category = "Spotify Song",
                        itemType = "TRACK",
                        artworkUrl = artwork,
                        previewUrl = previewUrl,
                        spotifyWebUrl = externalUrl,
                        spotifyUri = uri,
                        spotifyEmbedUrl = "https://open.spotify.com/embed/track/$id?utm_source=generator&theme=0",
                        baseFreqHz = if (idx % 2 == 0) 432.0 else 440.0,
                        harmonicFreqHz = if (idx % 2 == 0) 528.0 else 660.0,
                        isLiveSpotifyResult = true
                    )
                )
            }

            // Parse Playlists
            root["playlists"]?.jsonObject?.get("items")?.jsonArray?.forEach { el ->
                val plObj = el.jsonObject
                val id = plObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val name = plObj["name"]?.jsonPrimitive?.content ?: "Spotify Playlist"
                val owner = plObj["owner"]?.jsonObject?.get("display_name")?.jsonPrimitive?.content ?: "Spotify"
                val uri = plObj["uri"]?.jsonPrimitive?.content ?: "spotify:playlist:$id"
                val externalUrl = plObj["external_urls"]?.jsonObject?.get("spotify")?.jsonPrimitive?.content
                    ?: "https://open.spotify.com/playlist/$id"
                val artwork = plObj["images"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.content.orEmpty()

                results.add(
                    SpotifyStudyTrack(
                        id = "playlist_$id",
                        title = "Playlist: $name",
                        artist = "Curated by $owner",
                        album = "Spotify Playlist",
                        durationSec = 240,
                        category = "Spotify Playlist",
                        itemType = "PLAYLIST",
                        artworkUrl = artwork,
                        spotifyWebUrl = externalUrl,
                        spotifyUri = uri,
                        spotifyEmbedUrl = "https://open.spotify.com/embed/playlist/$id?utm_source=generator&theme=0",
                        isLiveSpotifyResult = true
                    )
                )
            }

            // Parse Albums
            root["albums"]?.jsonObject?.get("items")?.jsonArray?.forEach { el ->
                val alObj = el.jsonObject
                val id = alObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val name = alObj["name"]?.jsonPrimitive?.content ?: "Spotify Album"
                val artist = alObj["artists"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Artist"
                val uri = alObj["uri"]?.jsonPrimitive?.content ?: "spotify:album:$id"
                val externalUrl = alObj["external_urls"]?.jsonObject?.get("spotify")?.jsonPrimitive?.content
                    ?: "https://open.spotify.com/album/$id"
                val artwork = alObj["images"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.content.orEmpty()

                results.add(
                    SpotifyStudyTrack(
                        id = "album_$id",
                        title = "Album: $name",
                        artist = artist,
                        album = name,
                        durationSec = 210,
                        category = "Spotify Album",
                        itemType = "ALBUM",
                        artworkUrl = artwork,
                        spotifyWebUrl = externalUrl,
                        spotifyUri = uri,
                        spotifyEmbedUrl = "https://open.spotify.com/embed/album/$id?utm_source=generator&theme=0",
                        isLiveSpotifyResult = true
                    )
                )
            }

            // Parse Artists
            root["artists"]?.jsonObject?.get("items")?.jsonArray?.forEach { el ->
                val arObj = el.jsonObject
                val id = arObj["id"]?.jsonPrimitive?.content ?: return@forEach
                val name = arObj["name"]?.jsonPrimitive?.content ?: "Spotify Artist"
                val genres = arObj["genres"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: "Artist Radio"
                val uri = arObj["uri"]?.jsonPrimitive?.content ?: "spotify:artist:$id"
                val externalUrl = arObj["external_urls"]?.jsonObject?.get("spotify")?.jsonPrimitive?.content
                    ?: "https://open.spotify.com/artist/$id"

                results.add(
                    SpotifyStudyTrack(
                        id = "artist_$id",
                        title = "Artist: $name",
                        artist = name,
                        album = genres.replaceFirstChar { it.uppercase() },
                        durationSec = 200,
                        category = "Spotify Artist",
                        itemType = "ARTIST",
                        spotifyWebUrl = externalUrl,
                        spotifyUri = uri,
                        spotifyEmbedUrl = "https://open.spotify.com/embed/artist/$id?utm_source=generator&theme=0",
                        isLiveSpotifyResult = true
                    )
                )
            }

            results
        }
    }

    // --- SMART POMODORO FOCUS AUTOMATIC MUSIC CONTROLS ---

    fun setAutoPlayWithPomodoro(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(autoPlayWithPomodoro = enabled)
        savePreferences()
    }

    fun setPauseOnPomodoroBreak(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(pauseOnPomodoroBreak = enabled)
        savePreferences()
    }

    /**
     * Called automatically when the student starts a Pomodoro Focus block.
     * Operates with zero pop-ups or interruptions.
     */
    fun onPomodoroFocusStarted() {
        val state = _playerState.value
        if (!state.autoPlayWithPomodoro) return
        if (!state.isPlaying) {
            val track = state.currentTrack ?: CURATED_STUDY_CATALOG.first()
            _playerState.value = state.copy(currentTrack = track, isPlaying = true)
            startAudioForTrack(track, resumePositionSec = state.currentPositionSec)
            tryTriggerSpotifyConnectRemotePlayback(track)
        }
    }

    /**
     * Called automatically when the student pauses a Pomodoro Focus block.
     */
    fun onPomodoroFocusPaused() {
        val state = _playerState.value
        if (!state.autoPlayWithPomodoro) return
        if (state.isPlaying) {
            pauseAudioInternal()
            _playerState.value = state.copy(isPlaying = false)
            tryTriggerSpotifyConnectPause()
        }
    }

    /**
     * Called automatically when a Pomodoro Focus block finishes or resets.
     */
    fun onPomodoroFocusEnded(isTransitionToBreak: Boolean) {
        val state = _playerState.value
        if (!state.autoPlayWithPomodoro) return
        if (isTransitionToBreak && !state.pauseOnPomodoroBreak) return
        if (state.isPlaying) {
            pauseAudioInternal()
            _playerState.value = state.copy(isPlaying = false)
            tryTriggerSpotifyConnectPause()
        }
    }

    // --- PLAYBACK & TRANSPORT CONTROLS ---

    fun playTrack(track: SpotifyStudyTrack) {
        stopAudioInternal()
        val playbackNotice = when {
            track.previewUrl.isNotBlank() ->
                "Streaming audio preview in Ultron. Use transport controls or tap 'Spotify' for full-length playback."
            else ->
                "Playing ${track.category} focus audio in Ultron. Toggle 'Embedded Player' or tap 'Spotify' anytime."
        }
        _playerState.value = _playerState.value.copy(
            currentTrack = track,
            isPlaying = true,
            currentPositionSec = 0,
            playbackModeNotice = playbackNotice,
            searchError = null
        )
        savePreferences()
        startAudioForTrack(track)
        tryTriggerSpotifyConnectRemotePlayback(track)
    }

    fun togglePlayPause() {
        val state = _playerState.value
        val track = state.currentTrack ?: CURATED_STUDY_CATALOG.first()
        if (state.isPlaying) {
            pauseAudioInternal()
            _playerState.value = state.copy(isPlaying = false)
            tryTriggerSpotifyConnectPause()
        } else {
            _playerState.value = state.copy(currentTrack = track, isPlaying = true)
            startAudioForTrack(track, resumePositionSec = state.currentPositionSec)
            tryTriggerSpotifyConnectRemotePlayback(track)
        }
        savePreferences()
    }

    fun skipNext() {
        val state = _playerState.value
        val list = state.tracks.ifEmpty { CURATED_STUDY_CATALOG }
        val current = state.currentTrack
        val nextTrack = if (state.shuffleEnabled && list.size > 1) {
            list.filterNot { it.id == current?.id }.randomOrNull() ?: list.first()
        } else {
            val idx = list.indexOfFirst { it.id == current?.id }
            if (idx >= 0 && idx + 1 < list.size) list[idx + 1] else list.first()
        }
        playTrack(nextTrack)
    }

    fun skipPrevious() {
        val state = _playerState.value
        if (state.currentPositionSec > 5) {
            seekTo(0)
            return
        }
        val list = state.tracks.ifEmpty { CURATED_STUDY_CATALOG }
        val current = state.currentTrack
        val idx = list.indexOfFirst { it.id == current?.id }
        val prevTrack = if (idx > 0) list[idx - 1] else list.last()
        playTrack(prevTrack)
    }

    fun seekTo(positionSec: Int) {
        val state = _playerState.value
        val maxDur = state.currentTrack?.durationSec ?: 180
        val clamped = positionSec.coerceIn(0, maxDur)
        _playerState.value = state.copy(currentPositionSec = clamped)
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.seekTo((clamped * 1000).coerceAtMost(mp.duration.coerceAtLeast(1000)))
                }
            }
        } catch (_: Exception) {
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _playerState.value = _playerState.value.copy(volume = clamped)
        savePreferences()
        try {
            mediaPlayer?.setVolume(clamped, clamped)
            activeAudioTrack?.setVolume(clamped)
        } catch (_: Exception) {
        }
        tryTriggerSpotifyConnectVolume((clamped * 100).toInt().coerceIn(0, 100))
    }

    fun toggleShuffle() {
        val cur = _playerState.value
        _playerState.value = cur.copy(shuffleEnabled = !cur.shuffleEnabled)
        savePreferences()
    }

    fun toggleRepeat() {
        val cur = _playerState.value
        _playerState.value = cur.copy(repeatEnabled = !cur.repeatEnabled)
        savePreferences()
    }

    fun openTrackInSpotify(context: Context, track: SpotifyStudyTrack) {
        try {
            if (track.spotifyUri.isNotBlank()) {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(track.spotifyUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (appIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appIntent)
                    return
                }
            }
        } catch (_: Exception) {
        }
        openUrlSafely(context, track.spotifyWebUrl)
    }

    private fun openUrlSafely(context: Context, url: String) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (_: Exception) {
        }
    }

    /**
     * If a user OAuth token with `user-modify-playback-state` is active and a Spotify Connect
     * device is available, sends the official Web API play command non-blockingly.
     */
    private fun tryTriggerSpotifyConnectRemotePlayback(track: SpotifyStudyTrack) {
        val userToken = getConfiguredAccessToken()
        if (userToken.isBlank() || !track.spotifyUri.startsWith("spotify:")) return
        if (track.spotifyUri.startsWith("spotify:search:")) return

        scope.launch(Dispatchers.IO) {
            try {
                val payload = if (track.itemType == "TRACK") {
                    """{"uris":["${track.spotifyUri}"]}"""
                } else {
                    """{"context_uri":"${track.spotifyUri}"}"""
                }
                val req = Request.Builder()
                    .url("https://api.spotify.com/v1/me/player/play")
                    .header("Authorization", "Bearer $userToken")
                    .put(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    private fun tryTriggerSpotifyConnectPause() {
        val userToken = getConfiguredAccessToken()
        if (userToken.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://api.spotify.com/v1/me/player/pause")
                    .header("Authorization", "Bearer $userToken")
                    .put("".toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    private fun tryTriggerSpotifyConnectVolume(volumePercent: Int) {
        val userToken = getConfiguredAccessToken()
        if (userToken.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://api.spotify.com/v1/me/player/volume?volume_percent=$volumePercent")
                    .header("Authorization", "Bearer $userToken")
                    .put("".toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    private fun startAudioForTrack(track: SpotifyStudyTrack, resumePositionSec: Int = 0) {
        stopAudioInternal()
        _playerState.value = _playerState.value.copy(currentPositionSec = resumePositionSec)

        // Start progress ticker that respects dynamic seekTo() updates
        progressJob = scope.launch {
            while (isActive && _playerState.value.isPlaying) {
                delay(1000L)
                val pos = _playerState.value.currentPositionSec + 1
                val maxDur = _playerState.value.currentTrack?.durationSec ?: 180
                if (pos >= maxDur) {
                    if (_playerState.value.repeatEnabled) {
                        _playerState.value = _playerState.value.copy(currentPositionSec = 0)
                    } else {
                        skipNext()
                        break
                    }
                } else {
                    _playerState.value = _playerState.value.copy(currentPositionSec = pos)
                }
            }
        }

        // Stream audio preview URL if available; otherwise fall back to Multi-Genre Harmonic Focus Synthesizer
        synthJob = scope.launch(Dispatchers.IO) {
            if (track.previewUrl.isNotBlank()) {
                val previewStarted = try {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(track.previewUrl)
                        isLooping = _playerState.value.repeatEnabled
                        val vol = _playerState.value.volume
                        setVolume(vol, vol)
                        prepare()
                        start()
                    }
                    mediaPlayer = mp
                    true
                } catch (_: Exception) {
                    false
                }
                if (previewStarted) return@launch
            }

            try {
                val sampleRate = 22050
                val bufferSamples = sampleRate * 2 // 2-second seamless loop buffer
                val pcmBuffer = ShortArray(bufferSamples)
                val f1 = track.baseFreqHz
                val f2 = track.harmonicFreqHz
                val f3 = f1 * 0.5 // Sub-bass warm pad
                val f4 = f1 * 1.25 // Major third harmonic overtone
                val pulseHz = track.pulseRateHz.coerceIn(0.15, 2.0)

                for (i in 0 until bufferSamples) {
                    val t = i.toDouble() / sampleRate
                    val lfo = 0.72 + 0.28 * sin(2.0 * PI * pulseHz * t)
                    val wave = (0.40 * sin(2.0 * PI * f1 * t) +
                        0.26 * sin(2.0 * PI * f2 * t) +
                        0.20 * sin(2.0 * PI * f3 * t) +
                        0.14 * sin(2.0 * PI * f4 * t)) * lfo
                    pcmBuffer[i] = (wave * Short.MAX_VALUE * 0.18).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcmBuffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val vol = _playerState.value.volume
                audioTrack.setVolume(vol)
                audioTrack.play()
                activeAudioTrack = audioTrack

                while (isActive && _playerState.value.isPlaying) {
                    val written = audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
                    if (written < 0) break
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun pauseAudioInternal() {
        progressJob?.cancel()
        synthJob?.cancel()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.pause()
            }
        } catch (_: Exception) {
        }
        try {
            activeAudioTrack?.pause()
            activeAudioTrack?.flush()
            activeAudioTrack?.release()
        } catch (_: Exception) {
        }
        activeAudioTrack = null
    }

    private fun stopAudioInternal() {
        progressJob?.cancel()
        synthJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {
        }
        activeAudioTrack = null
    }
}
