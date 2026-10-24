package com.volna.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.volna.player.data.Downloader
import com.volna.player.data.Http
import com.volna.player.data.Library
import com.volna.player.data.SoundCloud
import com.volna.player.player.PlayerHolder
import com.volna.player.player.StreamMode
import com.volna.player.player.Streamer
import kotlinx.coroutines.launch

class PlaylistViewModel : ViewModel() {
    var input by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var playlist by mutableStateOf<SoundCloud.Playlist?>(null)
        private set
    var notice by mutableStateOf<String?>(null)

    fun load(url: String = input) {
        if (url.isBlank() || loading) return
        input = url
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val p = SoundCloud.resolve(url)
                playlist = p
                Library.rememberSeeds(p.tracks)
            } catch (e: Exception) {
                error = Http.friendly(e)
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun PlaylistScreen(vm: PlaylistViewModel, sharedUrl: String?, onSharedConsumed: () -> Unit) {
    val ui by PlayerHolder.state.collectAsStateWithLifecycle()
    val lib by Library.tracks.collectAsStateWithLifecycle()
    val states by Downloader.states.collectAsStateWithLifecycle()
    val downloadedIds = remember(lib) { lib.mapNotNull { it.scId }.toHashSet() }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            vm.load(sharedUrl)
            onSharedConsumed()
        }
    }

    val pl = vm.playlist
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
    ) {
        item {
            SectionTitle("Плейлист", Modifier.padding(horizontal = 8.dp))
            Text(
                "Вставь ссылку на плейлист, трек или профиль SoundCloud. Можно просто нажать «Поделиться» в приложении SoundCloud и выбрать Волну.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                placeholder = { Text("https://soundcloud.com/…/sets/…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { vm.input = it.trim() }
                    }) { Icon(Icons.Rounded.ContentPaste, "Вставить") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); vm.load() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Button(
                onClick = { focus.clearFocus(); vm.load() },
                enabled = !vm.loading && vm.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Text("Загрузить", fontWeight = FontWeight.SemiBold)
                }
            }
            vm.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }

        if (pl != null) {
            item {
                val canDl = pl.tracks.count { Downloader.canDownload(it) }
                val already = pl.tracks.count { it.scId != null && it.scId in downloadedIds }
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(pl.artworkUrl, Modifier.size(96.dp), corner = 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            pl.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(pl.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "${pl.tracks.size} треков · можно скачать: $canDl" + if (already > 0) " · в медиатеке: $already" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { Streamer.start(StreamMode.PLAYLIST, pl.tracks) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Слушать")
                    }
                    FilledTonalButton(
                        onClick = {
                            val n = Downloader.enqueue(pl.tracks)
                            vm.notice = if (n > 0) "Скачиваю $n треков в медиатеку" else "Нечего скачивать: автор не разрешил или всё уже скачано"
                        },
                        enabled = canDl > 0,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Скачать ($canDl)")
                    }
                }
                vm.notice?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            itemsIndexed(pl.tracks, key = { i, t -> "$i:${t.key}" }) { i, t ->
                TrackRow(
                    track = t,
                    active = ui.track?.scId == t.scId,
                    onClick = {
                        if (!t.isPreviewOnly) Streamer.start(StreamMode.PLAYLIST, pl.tracks.drop(i))
                    },
                    trailing = {
                        DownloadBadge(t, t.scId != null && t.scId in downloadedIds, t.scId?.let { states[it] })
                    },
                )
            }
        }
    }
}
