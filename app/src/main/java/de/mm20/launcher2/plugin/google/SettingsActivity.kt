package de.mm20.launcher2.plugin.google

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiClient: GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = GoogleApiClient(this)
        enableEdgeToEdge()
        runAuthCallback(intent)
        setContent {
            val state by apiClient.loginState.collectAsStateWithLifecycle(null)
            val darkMode = isSystemInDarkTheme()
            val theme = if (darkMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(this)
                } else {
                    darkColorScheme()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicLightColorScheme(this)
                } else {
                    lightColorScheme()
                }
            }
            MaterialTheme(theme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (state is LoginState.LoggedOut) {
                                Text(
                                    stringResource(R.string.sign_in),
                                    style = MaterialTheme.typography.headlineMedium
                                )

                                OutlinedButton(
                                    modifier = Modifier.padding(top = 16.dp),
                                    onClick = {
                                        lifecycleScope.launch {
                                            apiClient.signIn(this@SettingsActivity)
                                        }
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 10.dp
                                    ),
                                ) {
                                    Image(
                                        painterResource(R.drawable.ic_google_signin),
                                        null,
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .size(20.dp)
                                    )
                                    Text(
                                        stringResource(R.string.sign_in_button),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else if (state is LoginState.LoggedIn) {
                                val state = state as LoginState.LoggedIn

                                Box(modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .size(72.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    if (state.picture != null) {
                                        AsyncImage(
                                            model = state.picture,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            state.displayName.firstOrNull()?.toString() ?: "",
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.align(Alignment.Center),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                }

                                Text(
                                    stringResource(R.string.signed_in, state.displayName),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Button(
                                    onClick = {
                                        apiClient.signOut()
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Logout,
                                        null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Text(
                                        stringResource(R.string.sign_out),
                                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runAuthCallback(intent)
    }

    private fun runAuthCallback(intent: Intent) {
        Log.d("MM20", "onNewIntent: ${intent.data}")
        val code = intent.data?.getQueryParameter("code") ?: return
        Log.d("MM20", "onNewIntent:$code")


        apiClient.authCallback(code)
    }
}