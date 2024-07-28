package de.mm20.launcher2.plugin.google

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.oauth2.Oauth2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface LoginState {
    data class LoggedIn(val displayName: String, val picture: String?) : LoginState
    data object LoggedOut : LoginState
}

class GoogleApiClient private constructor(private val context: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val redirectUri = "${context.packageName}:/google-auth-redirect"

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val auth = GoogleAuthorizationCodeFlow.Builder(
        transport,
        jsonFactory,
        GoogleClientSecrets.load(
            jsonFactory,
            context.resources.openRawResource(R.raw.google_auth).reader()
        ),
        SCOPES
    ).setCredentialDataStore(
        FileDataStoreFactory(context.filesDir).getDataStore("google_auth")
    ).build()

    private val _loginState = MutableStateFlow<LoginState?>(null)
    val loginState: Flow<LoginState?> = _loginState

    init {
        scope.launch {
            val account = withContext(Dispatchers.IO) {
                val credential = auth.loadCredential("default")
                if (credential == null) {
                    LoginState.LoggedOut
                } else {
                    LoginState.LoggedIn(
                        prefs.getString("displayName", null) ?: "",
                        prefs.getString("picture", null),
                    )
                }
            }
            _loginState.value = account
        }
    }

    fun authCallback(code: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val response = try {
                    auth.newTokenRequest(code).setRedirectUri(
                        redirectUri
                    ).execute()
                } catch (e: IOException) {
                    Log.e("GoogleApiClient", "Failed to get token", e)
                    return@withContext
                }
                auth.createAndStoreCredential(response, "default")
                val (displayName, picture) = getAccountInfo()
                prefs.edit {
                    putString("displayName", displayName)
                    putString("picture", picture)
                }
                _loginState.value =
                    LoginState.LoggedIn(displayName = displayName, picture = picture)
            }
        }
    }

    fun signIn(context: Activity) {
        val url = auth
            .newAuthorizationUrl()
            .setRedirectUri(redirectUri)
            .toString()
        val themeColor = 0xFF4285f4.toInt()

        val customTabsIntent = CustomTabsIntent
            .Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(themeColor)
                    .setNavigationBarColor(themeColor)
                    .build()
            )
            .build()


        customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    fun signOut() {
        scope.launch {
            withContext(Dispatchers.IO) {
                auth.credentialDataStore.clear()
                _loginState.value = LoginState.LoggedOut
            }
        }
    }

    private suspend fun getRequestInitializer(): HttpRequestInitializer? {
        val credential = getCredential() ?: return null

        return HttpRequestInitializer { request ->
            credential.initialize(request)
            request?.connectTimeout = 5000
            request?.readTimeout = 10000
        }
    }

    private suspend fun getCredential(): Credential? {
        return withContext(Dispatchers.IO) {
            val credential: Credential? = auth.loadCredential("default")
            if ((credential?.expiresInSeconds ?: 0) < 5 * 60) {
                try {
                    if (credential?.refreshToken() == false) return@withContext null
                } catch (e: IOException) {
                    Log.e("GoogleApiClient", "Failed to refresh token", e)
                    return@withContext null
                }
            }
            return@withContext credential
        }
    }

    private suspend fun getAccountInfo(): Pair<String, String?> {
        val requestInitializer = getRequestInitializer() ?: return "" to null
        val jsonFactory = GsonFactory.getDefaultInstance()
        val oauth2 = Oauth2.Builder(transport, jsonFactory, requestInitializer).build()
        try {
            val meResponse = withContext(Dispatchers.IO) {
                oauth2.userinfo().v2().me().get().execute()
            }
            if (meResponse != null) {
                return meResponse.name to meResponse.picture
            }
        } catch (e: IOException) {
            Log.e("GoogleApiClient", "Failed to get account info", e)
        }
        return "" to null
    }

    suspend fun driveFileSearch(query: String): List<File> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val drive = Drive.Builder(transport, jsonFactory, requestInitializer).build()
            val request = drive.files().list().apply {
                q = "name contains '${query.replace("'", "")}'"
                pageSize = 20
                fields =
                    "files(id, webViewLink, size, name, mimeType, owners, imageMediaMetadata, videoMediaMetadata)"
                corpora = "user"
            }
            val response = try {
                request.execute()
            } catch (e: IOException) {
                Log.e("GoogleApiClient", "Failed to search drive", e)
                return@withContext emptyList()
            } catch (e: Error) {
                Log.e("GoogleApiClient", "Failed to search drive", e)
                return@withContext emptyList()
            }

            response.files ?: emptyList()
        }
    }

    suspend fun driveFileById(id: String): File? {
        val requestInitializer = getRequestInitializer() ?: return null
        return withContext(Dispatchers.IO) {
            val drive = Drive.Builder(transport, jsonFactory, requestInitializer).build()
            val request = drive.files().get(id).apply {
                fields =
                    "id, webViewLink, size, name, mimeType, owners, imageMediaMetadata, videoMediaMetadata"
            }
            val result = request.execute()
            result
        }
    }

    companion object {
        private lateinit var instance: GoogleApiClient
        operator fun invoke(context: Context): GoogleApiClient {
            if (!::instance.isInitialized) {
                instance = GoogleApiClient(context.applicationContext as Application)
            }
            return instance
        }

        private val SCOPES =
            setOf("https://www.googleapis.com/auth/drive.metadata.readonly", "profile")
    }
}