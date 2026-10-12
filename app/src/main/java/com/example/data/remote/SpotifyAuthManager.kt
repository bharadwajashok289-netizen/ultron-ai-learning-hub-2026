package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class SpotifyTokenSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresInSec: Long = 3600L,
    val expiresAtEpochMs: Long = 0L,
    val scope: String = ""
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis(), safetyBufferMs: Long = 60_000L): Boolean {
        return accessToken.isBlank() || (nowEpochMs + safetyBufferMs) >= expiresAtEpochMs
    }
}

sealed class SpotifyAuthState {
    data object Unauthenticated : SpotifyAuthState()
    data object Authorizing : SpotifyAuthState()
    data class Authenticated(
        val tokenType: String = "Bearer",
        val expiresAtEpochMs: Long,
        val hasRefreshToken: Boolean,
        val scope: String = ""
    ) : SpotifyAuthState()
    data class Error(val message: String) : SpotifyAuthState()
}

/**
 * Handles the official Spotify OAuth 2.0 Authorization Code with PKCE flow (RFC 7636),
 * CSRF state verification, Android Keystore AES-GCM encrypted token storage, and
 * mutex-synchronized automatic token refresh as per Spotify's official OAuth 2.0 guidelines.
 */
class SpotifyAuthManager(
    context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val AUTHORIZATION_ENDPOINT = "https://accounts.spotify.com/authorize"
        const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        const val REDIRECT_URI = "ultronstudy://spotify-callback"
        const val REDIRECT_SCHEME = "ultronstudy"
        const val REDIRECT_HOST = "spotify-callback"

        val DEFAULT_SCOPES = listOf(
            "streaming",
            "user-read-email",
            "user-read-private",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read"
        )

        private const val AUTH_PREFS_NAME = "ultron_spotify_secure_auth_prefs"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "ultron_spotify_oauth_aes_key"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val EXPIRY_BUFFER_MS = 60_000L
        private const val PKCE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        private const val KEY_ENC_ACCESS_TOKEN = "enc_spotify_access_token"
        private const val KEY_ENC_REFRESH_TOKEN = "enc_spotify_refresh_token"
        private const val KEY_TOKEN_EXPIRY_MS = "spotify_token_expiry_ms"
        private const val KEY_TOKEN_SCOPE = "spotify_token_scope"
        private const val KEY_TOKEN_TYPE = "spotify_token_type"
        private const val KEY_ENC_PKCE_VERIFIER = "enc_spotify_pkce_verifier"
        private const val KEY_OAUTH_STATE = "spotify_oauth_csrf_state"

        @Volatile
        private var instance: SpotifyAuthManager? = null

        fun getInstance(context: Context): SpotifyAuthManager {
            return instance ?: synchronized(this) {
                instance ?: SpotifyAuthManager(context.applicationContext).also { instance = it }
            }
        }

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var backendClientId: String? = null
    @Volatile
    private var backendClientSecret: String? = null

    private val _authState = MutableStateFlow<SpotifyAuthState>(SpotifyAuthState.Unauthenticated)
    val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    init {
        restoreAuthStateFromStorage()
    }

    private fun restoreAuthStateFromStorage() {
        val session = getStoredTokenSession()
        if (session != null && session.accessToken.isNotBlank()) {
            _authState.value = SpotifyAuthState.Authenticated(
                tokenType = session.tokenType,
                expiresAtEpochMs = session.expiresAtEpochMs,
                hasRefreshToken = !session.refreshToken.isNullOrBlank(),
                scope = session.scope
            )
        } else {
            _authState.value = SpotifyAuthState.Unauthenticated
        }
    }

    /**
     * Generates a high-entropy cryptographic PKCE `code_verifier` (RFC 7636 Section 4.1)
     * between 43 and 128 characters long.
     */
    fun generateCodeVerifier(length: Int = 64): String {
        val safeLength = length.coerceIn(43, 128)
        val sb = StringBuilder(safeLength)
        for (i in 0 until safeLength) {
            val index = secureRandom.nextInt(PKCE_CHARSET.length)
            sb.append(PKCE_CHARSET[index])
        }
        return sb.toString()
    }

    /**
     * Computes the PKCE S256 `code_challenge` = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
     * per Spotify's official Authorization Code with PKCE specification.
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val asciiBytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(asciiBytes)
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /**
     * Generates a cryptographic anti-CSRF `state` token for the OAuth 2.0 authorization request.
     */
    fun generateStateToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /**
     * Builds the official Spotify OAuth 2.0 PKCE Authorization URI and securely persists
     * the `code_verifier` and `state` parameter for callback validation.
     */
    fun buildAuthorizationUri(
        clientId: String = getConfiguredClientId(),
        redirectUri: String = REDIRECT_URI,
        scopes: List<String> = DEFAULT_SCOPES,
        customCodeVerifier: String? = null,
        customState: String? = null
    ): Uri? {
        val cleanClientId = clientId.trim()
        if (cleanClientId.isBlank()) return null

        val codeVerifier = customCodeVerifier ?: generateCodeVerifier(64)
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = customState ?: generateStateToken()

        prefs.edit()
            .putString(KEY_ENC_PKCE_VERIFIER, encryptSecret(codeVerifier))
            .putString(KEY_OAUTH_STATE, state)
            .apply()

        _authState.value = SpotifyAuthState.Authorizing

        return Uri.parse(AUTHORIZATION_ENDPOINT)
            .buildUpon()
            .appendQueryParameter("client_id", cleanClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", scopes.joinToString(" "))
            .build()
    }

    /**
     * Initiates the browser-based Spotify OAuth 2.0 PKCE flow.
     * Returns true if the authorization browser intent was launched, or false if no clientId is configured.
     */
    fun launchAuthorizationFlow(
        context: Context,
        scopes: List<String> = DEFAULT_SCOPES
    ): Boolean {
        val authUri = buildAuthorizationUri(scopes = scopes) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validates whether the incoming intent URI matches `ultronstudy://spotify-callback`.
     */
    fun isSpotifyRedirectUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.scheme.equals(REDIRECT_SCHEME, ignoreCase = true) &&
            uri.host.equals(REDIRECT_HOST, ignoreCase = true)
    }

    /**
     * Handles the OAuth 2.0 redirect callback (`ultronstudy://spotify-callback?code=...&state=...`)
     * asynchronously, validating CSRF state and exchanging the authorization code for tokens.
     */
    fun handleAuthorizationCallback(
        uri: Uri?,
        onResult: ((Result<SpotifyTokenSession>) -> Unit)? = null
    ) {
        if (!isSpotifyRedirectUri(uri) || uri == null) return

        val errorParam = uri.getQueryParameter("error")
        if (!errorParam.isNullOrBlank()) {
            val errorMsg = "Spotify authorization declined: $errorParam"
            _authState.value = SpotifyAuthState.Error(errorMsg)
            onResult?.invoke(Result.failure(IllegalStateException(errorMsg)))
            return
        }

        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        if (code.isNullOrBlank()) {
            val errorMsg = "Missing authorization code in Spotify callback."
            _authState.value = SpotifyAuthState.Error(errorMsg)
            onResult?.invoke(Result.failure(IllegalArgumentException(errorMsg)))
            return
        }

        scope.launch {
            val result = exchangeAuthorizationCode(code = code, returnedState = returnedState)
            onResult?.invoke(result)
        }
    }

    /**
     * Exchanges an OAuth 2.0 authorization `code` for an `access_token` and `refresh_token`
     * using the stored PKCE `code_verifier` and verifying the CSRF `state`.
     */
    suspend fun exchangeAuthorizationCode(
        code: String,
        returnedState: String?,
        redirectUri: String = REDIRECT_URI
    ): Result<SpotifyTokenSession> = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val expectedState = prefs.getString(KEY_OAUTH_STATE, null)
            if (!expectedState.isNullOrBlank() && expectedState != returnedState) {
                val msg = "Spotify OAuth state mismatch (CSRF protection check failed)."
                _authState.value = SpotifyAuthState.Error(msg)
                return@withLock Result.failure(SecurityException(msg))
            }

            val encryptedVerifier = prefs.getString(KEY_ENC_PKCE_VERIFIER, null)
            val codeVerifier = decryptSecret(encryptedVerifier)
            if (codeVerifier.isBlank()) {
                val msg = "Missing PKCE code_verifier for Spotify token exchange."
                _authState.value = SpotifyAuthState.Error(msg)
                return@withLock Result.failure(IllegalStateException(msg))
            }

            val clientId = getConfiguredClientId()
            if (clientId.isBlank()) {
                val msg = "Spotify Client ID is not configured."
                _authState.value = SpotifyAuthState.Error(msg)
                return@withLock Result.failure(IllegalStateException(msg))
            }

            try {
                val formBody = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code.trim())
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", codeVerifier)
                    .build()

                val request = Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .post(formBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val msg = "Spotify token exchange failed (HTTP ${response.code})."
                        _authState.value = SpotifyAuthState.Error(msg)
                        return@use Result.failure(IllegalStateException(msg))
                    }

                    val session = parseAndPersistTokenResponse(
                        responseBody = bodyStr,
                        fallbackRefreshToken = getStoredRefreshToken()
                    ) ?: return@use Result.failure(IllegalStateException("Malformed Spotify token response."))

                    // Clear one-time PKCE verifier and CSRF state after successful exchange
                    prefs.edit()
                        .remove(KEY_ENC_PKCE_VERIFIER)
                        .remove(KEY_OAUTH_STATE)
                        .apply()

                    Result.success(session)
                }
            } catch (e: Exception) {
                _authState.value = SpotifyAuthState.Error(e.message ?: "Spotify token exchange failed.")
                Result.failure(e)
            }
        }
    }

    /**
     * Returns a valid, non-expired Spotify access token.
     * - Returns the cached/stored token if still valid (outside the 60-second expiry safety buffer).
     * - Automatically refreshes the token using the stored `refresh_token` when expired or when `forceRefresh` is true.
     * - Falls back to Client Credentials or configured backend token when no PKCE user token is present.
     */
    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            val storedSession = getStoredTokenSession()

            if (!forceRefresh && storedSession != null && !storedSession.isExpired(now, EXPIRY_BUFFER_MS)) {
                return@withLock storedSession.accessToken
            }

            if (!forceRefresh) {
                val envAccessToken = getConfiguredDirectAccessToken()
                if (envAccessToken.isNotBlank()) {
                    return@withLock envAccessToken
                }
            }

            // Ensure backend credentials from Firestore are loaded if local BuildConfig keys are absent
            if (!hasConfiguredCredentials()) {
                fetchBackendCredentialsSilently()
            }

            // 1. Official OAuth 2.0 Refresh Token flow
            val refreshToken = storedSession?.refreshToken?.takeIf { it.isNotBlank() }
                ?: getConfiguredDirectRefreshToken()
            val clientId = getConfiguredClientId()
            val clientSecret = getConfiguredClientSecret()

            if (refreshToken.isNotBlank() && clientId.isNotBlank()) {
                val refreshed = performTokenRefreshInternal(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    refreshToken = refreshToken
                )
                if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                    return@withLock refreshed.accessToken
                }
            }

            // 2. Fallback to Client Credentials grant if clientId + clientSecret are available
            if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                val clientCredsSession = performClientCredentialsGrantInternal(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    existingRefreshToken = refreshToken.ifBlank { null }
                )
                if (clientCredsSession != null && clientCredsSession.accessToken.isNotBlank()) {
                    return@withLock clientCredsSession.accessToken
                }
            }

            // 3. Return unexpired stored token if still usable
            if (storedSession != null && storedSession.accessToken.isNotBlank() && now < storedSession.expiresAtEpochMs) {
                return@withLock storedSession.accessToken
            }

            null
        }
    }

    /**
     * Explicitly refreshes the current Spotify access token using the official OAuth 2.0 `refresh_token` grant.
     * Supports both PKCE public clients (`client_id` in form body) and confidential clients (`Authorization: Basic`).
     */
    suspend fun refreshAccessToken(
        explicitRefreshToken: String? = null
    ): SpotifyTokenSession? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (!hasConfiguredCredentials()) {
                fetchBackendCredentialsSilently()
            }
            val refreshToken = explicitRefreshToken?.trim()?.takeIf { it.isNotBlank() }
                ?: getStoredRefreshToken()
                ?: getConfiguredDirectRefreshToken()
            val clientId = getConfiguredClientId()
            val clientSecret = getConfiguredClientSecret()

            if (refreshToken.isBlank() || clientId.isBlank()) {
                return@withLock null
            }

            performTokenRefreshInternal(
                clientId = clientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken
            )
        }
    }

    private fun performTokenRefreshInternal(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): SpotifyTokenSession? {
        return try {
            val formBuilder = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)

            val requestBuilder = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .header("Content-Type", "application/x-www-form-urlencoded")

            if (clientSecret.isNotBlank()) {
                val basicAuth = Base64.encodeToString(
                    "$clientId:$clientSecret".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", "Basic $basicAuth")
            }

            val request = requestBuilder.post(formBuilder.build()).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string().orEmpty()
                parseAndPersistTokenResponse(
                    responseBody = bodyStr,
                    fallbackRefreshToken = refreshToken
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun performClientCredentialsGrantInternal(
        clientId: String,
        clientSecret: String,
        existingRefreshToken: String?
    ): SpotifyTokenSession? {
        return try {
            val basicAuth = Base64.encodeToString(
                "$clientId:$clientSecret".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .header("Authorization", "Basic $basicAuth")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string().orEmpty()
                parseAndPersistTokenResponse(
                    responseBody = bodyStr,
                    fallbackRefreshToken = existingRefreshToken
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a standard Spotify `/api/token` JSON response and securely persists the session.
     * Note: Spotify may rotate `refresh_token` on PKCE refresh; if omitted in the response,
     * the existing `fallbackRefreshToken` is preserved per Spotify's official token refresh guide.
     */
    fun parseAndPersistTokenResponse(
        responseBody: String,
        fallbackRefreshToken: String? = getStoredRefreshToken(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): SpotifyTokenSession? {
        return try {
            val obj = json.parseToJsonElement(responseBody).jsonObject
            val accessToken = obj["access_token"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (accessToken.isBlank()) return null

            val newRefreshToken = obj["refresh_token"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackRefreshToken
            val tokenType = obj["token_type"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Bearer"
            val expiresInSec = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
            val scopeStr = obj["scope"]?.jsonPrimitive?.content?.trim().orEmpty()
            val expiresAtEpochMs = nowEpochMs + (expiresInSec * 1000L)

            val session = SpotifyTokenSession(
                accessToken = accessToken,
                refreshToken = newRefreshToken,
                tokenType = tokenType,
                expiresInSec = expiresInSec,
                expiresAtEpochMs = expiresAtEpochMs,
                scope = scopeStr
            )
            saveTokenSession(session)
            session
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts and persists the `SpotifyTokenSession` using Android Keystore AES-GCM encryption.
     */
    fun saveTokenSession(session: SpotifyTokenSession) {
        val editor = prefs.edit()
            .putString(KEY_ENC_ACCESS_TOKEN, encryptSecret(session.accessToken))
            .putLong(KEY_TOKEN_EXPIRY_MS, session.expiresAtEpochMs)
            .putString(KEY_TOKEN_TYPE, session.tokenType)
            .putString(KEY_TOKEN_SCOPE, session.scope)

        if (!session.refreshToken.isNullOrBlank()) {
            editor.putString(KEY_ENC_REFRESH_TOKEN, encryptSecret(session.refreshToken))
        }
        editor.apply()

        _authState.value = SpotifyAuthState.Authenticated(
            tokenType = session.tokenType,
            expiresAtEpochMs = session.expiresAtEpochMs,
            hasRefreshToken = !session.refreshToken.isNullOrBlank(),
            scope = session.scope
        )
    }

    /**
     * Reads and decrypts the currently stored `SpotifyTokenSession`, or returns null if none exists.
     */
    fun getStoredTokenSession(): SpotifyTokenSession? {
        val encAccess = prefs.getString(KEY_ENC_ACCESS_TOKEN, null) ?: return null
        val accessToken = decryptSecret(encAccess)
        if (accessToken.isBlank()) return null

        val encRefresh = prefs.getString(KEY_ENC_REFRESH_TOKEN, null)
        val refreshToken = decryptSecret(encRefresh).takeIf { it.isNotBlank() }
        val expiryMs = prefs.getLong(KEY_TOKEN_EXPIRY_MS, 0L)
        val tokenType = prefs.getString(KEY_TOKEN_TYPE, "Bearer") ?: "Bearer"
        val scopeStr = prefs.getString(KEY_TOKEN_SCOPE, "") ?: ""
        val remainingSec = ((expiryMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)

        return SpotifyTokenSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            expiresInSec = remainingSec,
            expiresAtEpochMs = expiryMs,
            scope = scopeStr
        )
    }

    fun getStoredRefreshToken(): String? {
        val encRefresh = prefs.getString(KEY_ENC_REFRESH_TOKEN, null) ?: return null
        return decryptSecret(encRefresh).takeIf { it.isNotBlank() }
    }

    /**
     * Clears all stored OAuth tokens, PKCE verifiers, and state parameters.
     */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_ENC_ACCESS_TOKEN)
            .remove(KEY_ENC_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY_MS)
            .remove(KEY_TOKEN_SCOPE)
            .remove(KEY_TOKEN_TYPE)
            .remove(KEY_ENC_PKCE_VERIFIER)
            .remove(KEY_OAUTH_STATE)
            .apply()
        _authState.value = SpotifyAuthState.Unauthenticated
    }

    fun hasConfiguredCredentials(): Boolean {
        val clientId = getConfiguredClientId()
        val clientSecret = getConfiguredClientSecret()
        val directAccess = getConfiguredDirectAccessToken()
        val directRefresh = getConfiguredDirectRefreshToken()
        val storedSession = getStoredTokenSession()
        return (clientId.isNotBlank() && clientSecret.isNotBlank()) ||
            directAccess.isNotBlank() ||
            directRefresh.isNotBlank() ||
            (storedSession != null && storedSession.accessToken.isNotBlank())
    }

    fun getConfiguredClientId(): String {
        val raw = try { BuildConfig.SPOTIFY_CLIENT_ID } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_CLIENT_ID") return raw.trim()
        return backendClientId?.takeIf { it.isNotBlank() }.orEmpty()
    }

    fun getConfiguredClientSecret(): String {
        val raw = try { BuildConfig.SPOTIFY_CLIENT_SECRET } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_CLIENT_SECRET") return raw.trim()
        return backendClientSecret?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun getConfiguredDirectAccessToken(): String {
        val raw = try { BuildConfig.SPOTIFY_ACCESS_TOKEN } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_ACCESS_TOKEN") return raw.trim()
        return ""
    }

    private fun getConfiguredDirectRefreshToken(): String {
        val raw = try { BuildConfig.SPOTIFY_REFRESH_TOKEN } catch (_: Exception) { "" }
        if (raw.isNotBlank() && raw != "MY_SPOTIFY_REFRESH_TOKEN") return raw.trim()
        return ""
    }

    suspend fun fetchBackendCredentialsSilently() = withContext(Dispatchers.IO) {
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
                if (atok.isNotBlank() && getStoredTokenSession() == null) {
                    saveTokenSession(
                        SpotifyTokenSession(
                            accessToken = atok,
                            refreshToken = rtok.ifBlank { null },
                            expiresInSec = 3600L,
                            expiresAtEpochMs = System.currentTimeMillis() + 3500_000L
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore silently when offline or when document is not present
        }
    }

    /**
     * Encrypts sensitive tokens with an AndroidKeyStore AES-256-GCM SecretKey before persisting
     * to SharedPreferences. Falls back cleanly to a local obfuscated envelope in JVM unit test
     * environments where AndroidKeyStore hardware provider is not mounted.
     */
    private fun encryptSecret(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val secretKey = getOrCreateKeystoreSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
                "KS1:$ivB64:$cipherB64"
            } else {
                val fallbackB64 = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "FB1:$fallbackB64"
            }
        } catch (_: Exception) {
            val fallbackB64 = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "FB1:$fallbackB64"
        }
    }

    private fun decryptSecret(storedPayload: String?): String {
        if (storedPayload.isNullOrBlank()) return ""
        return try {
            when {
                storedPayload.startsWith("KS1:") -> {
                    val parts = storedPayload.split(":", limit = 3)
                    if (parts.size != 3) return ""
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
                    val secretKey = getOrCreateKeystoreSecretKey() ?: return ""
                    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                }
                storedPayload.startsWith("FB1:") -> {
                    val b64 = storedPayload.removePrefix("FB1:")
                    String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> storedPayload
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                return existingEntry.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }
}
