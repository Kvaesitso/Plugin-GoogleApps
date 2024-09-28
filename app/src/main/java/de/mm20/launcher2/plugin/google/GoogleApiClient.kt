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
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.oauth2.Oauth2
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import com.google.api.services.calendar.Calendar
import com.google.api.services.tasks.Tasks

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
        val credential = getCredential()

        if (credential == null) {
            Log.w("GoogleApiClient", "Failed to get credential")
            return null
        }

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

    suspend fun driveFilesList(q: String): List<File> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val drive = Drive.Builder(transport, jsonFactory, requestInitializer).build()
            val request = drive.files().list().apply {
                setQ(q)
                pageSize = 20
                fields =
                    "files(id, webViewLink, webContentLink, size, name, mimeType, owners, imageMediaMetadata, videoMediaMetadata)"
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
                    "id, webViewLink, webContentLink, size, name, mimeType, owners, imageMediaMetadata, videoMediaMetadata"
            }
            val result = request.execute()
            result
        }
    }

    suspend fun calendarListsList() : List<CalendarListEntry> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.Builder(transport, jsonFactory, requestInitializer).build()
            val request = calendar.calendarList().list().apply {
                fields = "items(id, summary, summaryOverride, backgroundColor)"
            }
            val response = try {
                request.execute()
            } catch (e: IOException) {
                Log.e("GoogleApiClient", "Failed to list calendars", e)
                return@withContext emptyList()
            } catch (e: Error) {
                Log.e("GoogleApiClient", "Failed to list calendars", e)
                return@withContext emptyList()
            }

            response.items ?: emptyList()
        }
    }

    suspend fun calendarEventsList(
        calendarId: String,
        q: String? = null,
        timeMin: Long? = null,
        timeMax: Long? = null,
    ): List<Event> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.Builder(transport, jsonFactory, requestInitializer).build()
            val request = calendar.events().list(calendarId).apply {
                if (q != null) {
                    setQ(q)
                }
                if (timeMin != null) {
                    setTimeMin(DateTime(timeMin))
                }
                if (timeMax != null) {
                    setTimeMax(DateTime(timeMax))
                }
                maxResults = 20
                fields =
                    "items(id, summary, description, location, start, end, attendees, htmlLink)"
            }
            val response = try {
                request.execute()
            } catch (e: IOException) {
                Log.e("GoogleApiClient", "Failed to list calendar", e)
                return@withContext emptyList()
            } catch (e: Error) {
                Log.e("GoogleApiClient", "Failed to list calendar", e)
                return@withContext emptyList()
            }

            response.items ?: emptyList()
        }
    }

    suspend fun calendarEventById(calendarId: String, id: String): Event? {
        val requestInitializer = getRequestInitializer() ?: return null
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.Builder(transport, jsonFactory, requestInitializer).build()
            val request = calendar.events().get(calendarId, id).apply {
                fields =
                    "id, summary, description, location, start, end, attendees, htmlLink, completed"
            }
            val result = request.execute()
            result
        }
    }

    suspend fun tasklistsList(): List<TaskList> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val tasks = Tasks.Builder(transport, jsonFactory, requestInitializer).build()
            val request = tasks.tasklists().list().apply {
                fields = "items(id, title, updated)"
            }
            val response = try {
                request.execute()
            } catch (e: IOException) {
                Log.e("GoogleApiClient", "Failed to list tasklists", e)
                return@withContext emptyList()
            } catch (e: Error) {
                Log.e("GoogleApiClient", "Failed to list tasklists", e)
                return@withContext emptyList()
            }

            response.items ?: emptyList()
        }
    }

    suspend fun tasksList(
        tasklistId: String,
        dueMin: Long? = null,
        dueMax: Long? = null,
    ): List<Task> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val tasks = Tasks.Builder(transport, jsonFactory, requestInitializer).build()
            val request = tasks.tasks().list(tasklistId).apply {
                if (dueMin != null) {
                    setDueMin(DateTime(dueMin).toStringRfc3339())
                }
                if (dueMax != null) {
                    setDueMax(DateTime(dueMax).toStringRfc3339())
                }
                setShowHidden(true)
                setShowCompleted(true)
                maxResults = 20
                fields =
                    "items(id, title, due, webViewLink, status, notes, completed)"
            }
            val response = try {
                request.execute()
            } catch (e: IOException) {
                Log.e("GoogleApiClient", "Failed to list tasks", e)
                return@withContext emptyList()
            } catch (e: Error) {
                Log.e("GoogleApiClient", "Failed to list tasks", e)
                return@withContext emptyList()
            }

            response.items ?: emptyList()
        }
    }

    suspend fun taskById(tasklistId: String, id: String): Task? {
        val requestInitializer = getRequestInitializer() ?: return null
        return withContext(Dispatchers.IO) {
            val tasks = Tasks.Builder(transport, jsonFactory, requestInitializer).build()
            val request = tasks.tasks().get(tasklistId, id).apply {
                fields =
                    "id, title, due, webViewLink, status, notes, completed"
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
            setOf(
                "https://www.googleapis.com/auth/drive.metadata.readonly",
                "https://www.googleapis.com/auth/calendar.readonly",
                "https://www.googleapis.com/auth/tasks.readonly",
                "profile"
            )
    }
}