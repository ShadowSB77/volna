package com.volna.player.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.volna.player.player.PlaybackService
import com.volna.player.ui.theme.VColors
import com.volna.player.ui.theme.VolnaTheme
import kotlinx.coroutines.flow.MutableStateFlow

enum class Tab(val title: String, val icon: ImageVector) {
    WAVE("Волна", Icons.Rounded.Waves),
    LIBRARY("Медиатека", Icons.Rounded.LibraryMusic),
    PLAYLIST("Плейлист", Icons.Rounded.Link),
    EQ("Эквалайзер", Icons.Rounded.Equalizer),
}

class MainActivity : ComponentActivity() {
    private val sharedUrl = MutableStateFlow<String?>(null)
    private var controller: ListenableFuture<MediaController>? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        askNotifications()
        setContent {
            VolnaTheme {
                val shared by sharedUrl.collectAsStateWithLifecycle()
                VolnaRoot(shared) { sharedUrl.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Подключение к сервису: он показывает уведомление и играет в фоне.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedUrl.value = it }
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun VolnaRoot(sharedUrl: String?, onSharedConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.WAVE) }
    var showPlayer by remember { mutableStateOf(false) }
    val playlistVm: PlaylistViewModel = viewModel()

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) tab = Tab.PLAYLIST
    }

    Scaffold(
        containerColor = VColors.Bg,
        bottomBar = {
            Column {
                MiniPlayer(onOpen = { showPlayer = true })
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, t.title) },
                            label = { Text(t.title) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.WAVE -> WaveScreen()
                Tab.LIBRARY -> LibraryScreen()
                Tab.PLAYLIST -> PlaylistScreen(playlistVm, sharedUrl, onSharedConsumed)
                Tab.EQ -> EqScreen()
            }
        }
    }

    if (showPlayer) {
        PlayerSheet(
            onDismiss = { showPlayer = false },
            onOpenEq = { showPlayer = false; tab = Tab.EQ },
        )
    }
}
