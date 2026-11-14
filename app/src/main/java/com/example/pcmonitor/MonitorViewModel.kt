package com.example.pcmonitor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val spotify = SpotifyClient(app)

    private val _loggedIn = MutableStateFlow(spotify.isLoggedIn())
    val loggedIn: StateFlow<Boolean> = _loggedIn

    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback

    private val _playbackAt = MutableStateFlow(0L)
    val playbackAt: StateFlow<Long> = _playbackAt

    private val _art = MutableStateFlow<Bitmap?>(null)
    val art: StateFlow<Bitmap?> = _art

    private val _spotifyMessage = MutableStateFlow<String?>(null)
    val spotifyMessage: StateFlow<String?> = _spotifyMessage

    private var artUrl: String? = null

    init {
        viewModelScope.launch { spotifyLoop() }
    }

    private suspend fun spotifyLoop() {
        while (true) {
            if (_loggedIn.value) refreshPlayback()
            delay(Config.SPOTIFY_INTERVAL_MS)
        }
    }

    private suspend fun refreshPlayback() {
        try {
            val p = withContext(Dispatchers.IO) { spotify.currentPlayback() }
            _playbackAt.value = System.currentTimeMillis()
            _playback.value = p
            loadArt(p?.artUrl)
            _spotifyMessage.value = null
        } catch (e: NotAuthorizedException) {
            _loggedIn.value = false
            _playback.value = null
        } catch (e: Exception) {
            _spotifyMessage.value = e.message ?: "Erro ao falar com o Spotify"
        }
    }

    private suspend fun loadArt(url: String?) {
        if (url == artUrl) return
        artUrl = url
        if (url == null) {
            _art.value = null
            return
        }
        _art.value = withContext(Dispatchers.IO) {
            try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun login() = spotify.startLogin(getApplication())

    fun onAuthRedirect(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { spotify.finishLogin(uri) }
                _loggedIn.value = true
                _spotifyMessage.value = null
                refreshPlayback()
            } catch (e: Exception) {
                _spotifyMessage.value = e.message ?: "Falhou o login no Spotify"
            }
        }
    }

    fun logout() {
        spotify.logout()
        _loggedIn.value = false
        _playback.value = null
        _art.value = null
        artUrl = null
    }

    fun playPause() = spotifyCall {
        if (_playback.value?.isPlaying == true) spotify.pause() else spotify.play()
    }

    fun next() = spotifyCall { spotify.next() }

    fun previous() = spotifyCall { spotify.previous() }

    private fun spotifyCall(block: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                delay(400)
                refreshPlayback()
            } catch (e: NotAuthorizedException) {
                _loggedIn.value = false
            } catch (e: Exception) {
                _spotifyMessage.value = e.message ?: "Erro no comando do Spotify"
            }
        }
    }
}