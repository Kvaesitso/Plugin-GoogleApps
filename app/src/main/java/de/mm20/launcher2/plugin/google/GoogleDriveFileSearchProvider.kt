package de.mm20.launcher2.plugin.google

import android.content.Intent
import android.net.Uri
import android.util.Log
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.base.RefreshParams
import de.mm20.launcher2.sdk.base.SearchParams
import de.mm20.launcher2.sdk.files.File
import de.mm20.launcher2.sdk.files.FileDimensions
import de.mm20.launcher2.sdk.files.FileMetadata
import de.mm20.launcher2.sdk.files.FileProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GoogleDriveFileSearchProvider: FileProvider(
    QueryPluginConfig(
        storageStrategy = StorageStrategy.StoreCopy,
    )
) {
    private lateinit var apiClient: GoogleApiClient

    override fun onCreate(): Boolean {
        apiClient = GoogleApiClient(context!!)
        return super.onCreate()
    }

    override suspend fun search(query: String, params: SearchParams): List<File> {
        if (!params.allowNetwork) return emptyList()
        val files = apiClient.driveFileSearch(query)
        return files.mapNotNull { it.toFileResult() }
    }

    override suspend fun refresh(item: File, params: RefreshParams): File? {
        Log.d("MM20" , "Refreshing file ${item.id}")
        if (params.lastUpdated + 5.seconds.inWholeMilliseconds > System.currentTimeMillis()) {
            return item
        }
        return apiClient.driveFileById(item.id)?.toFileResult()
    }

    override suspend fun getPluginState(): PluginState {
        val loginState = apiClient.loginState.firstOrNull()
        if (loginState is LoginState.LoggedIn) {
            return PluginState.Ready(
                text = context!!.getString(R.string.drive_plugin_state_ready, loginState.displayName),
            )
        }
        return PluginState.SetupRequired(
            setupActivity = Intent(context!!, SettingsActivity::class.java),
            message = context!!.getString(R.string.drive_plugin_state_setup_required)
        )
    }

    private fun com.google.api.services.drive.model.File.toFileResult(): File? {
        return File(
            id = id,
            displayName = name ?: return null,
            mimeType = mimeType ?: "binary/octet-stream",
            isDirectory = mimeType == "application/vnd.google-apps.folder",
            size = getSize() ?: 0L,
            thumbnailUri = iconLink?.let { Uri.parse(it) },
            path = null,
            uri = webViewLink?.let { Uri.parse(it) } ?: webContentLink?.let { Uri.parse(it) } ?: return null,
            owner = owners?.firstOrNull()?.displayName,
            metadata = FileMetadata(
                dimensions = if (imageMediaMetadata?.width != null && imageMediaMetadata.height != null) {
                    FileDimensions(imageMediaMetadata.width, imageMediaMetadata.height)
                } else if (videoMediaMetadata?.width != null && videoMediaMetadata.height != null) {
                    FileDimensions(videoMediaMetadata.width, videoMediaMetadata.height)
                } else {
                    null
                },
            )
        )
    }
}