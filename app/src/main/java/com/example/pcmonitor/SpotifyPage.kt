package com.example.pcmonitor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val CardBg = Color(0xFF141922)
private val TextMain = Color(0xFFE6EAF2)
private val TextDim = Color(0xFF8A94A6)
private val Neutral = Color(0xFF232A38)
private val Red = Color(0xFFFF5C5C)
private val SpotifyGreen = Color(0xFF1DB954)

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Página 1: só Spotify, em ecrã inteiro. */
@Composable
fun SpotifyPage(vm: MonitorViewModel) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val fetchedAt by vm.playbackAt.collectAsStateWithLifecycle()
    val art by vm.art.collectAsStateWithLifecycle()
    val message by vm.spotifyMessage.collectAsStateWithLifecycle()

    // O Spotify só é consultado a cada 2 s; entre leituras a barra avança sozinha.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().padding(20.dp)) {
        if (!loggedIn) {
            LoginPrompt(vm, message)
            return@Box
        }

        val p = playback
        val shownMs = if (p == null) 0L else {
            val elapsed = if (p.isPlaying) (now - fetchedAt).coerceAtLeast(0) else 0L
            (p.progressMs + elapsed).coerceAtMost(p.durationMs)
        }
        val progress = if (p != null && p.durationMs > 0) shownMs.toFloat() / p.durationMs else 0f

        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Capa
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)).background(CardBg),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = art
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("♪", color = TextDim, fontSize = 72.sp)
                }
            }

            // Informação e controlos
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    p?.title ?: "Nada a tocar",
                    color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    p?.artist ?: "Põe uma música a tocar no Spotify",
                    color = TextDim, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!p?.album.isNullOrEmpty()) {
                    Text(
                        p!!.album, color = TextDim, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF2A3140))) {
                    Box(
                        Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                            .background(SpotifyGreen),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(shownMs), color = TextDim, fontSize = 12.sp)
                    Text(formatTime(p?.durationMs ?: 0L), color = TextDim, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center, // Centraliza os botões em bloco no meio
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton("⏮", 64.dp, Neutral, TextMain) { vm.previous() }
                    Spacer(Modifier.width(20.dp))
                    CircleButton(
                        if (p?.isPlaying == true) "⏸" else "▶", 84.dp, SpotifyGreen, Color.Black,
                    ) { vm.playPause() }
                    Spacer(Modifier.width(20.dp))
                    CircleButton("⏭", 64.dp, Neutral, TextMain) { vm.next() }
                }

                Spacer(Modifier.height(16.dp))
                val footer = message ?: p?.device?.takeIf { it.isNotEmpty() }?.let { "A tocar em: $it" }
                if (footer != null) {
                    Text(
                        footer, color = if (message != null) Red else TextDim, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginPrompt(vm: MonitorViewModel, message: String?) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Spotify", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(28.dp)).background(SpotifyGreen)
                .clickable { vm.login() }.padding(horizontal = 32.dp, vertical = 14.dp),
        ) { Text("Ligar ao Spotify", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = Red, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CircleButton(label: String, size: Dp, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val textModifier = if (label == "⏸") Modifier.offset(x = 1.dp) else Modifier
        Text(
            text = label,
            color = fg,
            fontSize = (size.value * 0.4f).sp,
            modifier = textModifier
        )
    }
}