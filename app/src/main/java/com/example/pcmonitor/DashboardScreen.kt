package com.example.pcmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B0E14)
private val DotActive = Color(0xFFE6EAF2)
private val DotInactive = Color(0xFF3A4254)

/** Três páginas: Relógio (0), Spotify (1) e Previsão do Tempo (2). */
@Composable
fun DashboardScreen(vm: MonitorViewModel) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val playback by vm.playback.collectAsState()
    val isPlaying = playback?.isPlaying == true

    LaunchedEffect(isPlaying) {
        coroutineScope.launch {
            if (isPlaying) {
                pagerState.animateScrollToPage(1) // Vai para o Spotify
            } else {
                pagerState.animateScrollToPage(0) // Volta para o Relógio
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> StandByClockPage(
                    onWeatherClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2) // Vai para a 3ª página (Previsão do Tempo)
                        }
                    }
                )
                1 -> SpotifyPage(vm)
                2 -> WeatherForecastPage()
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .background(if (pagerState.currentPage == i) DotActive else DotInactive),
                )
            }
        }
    }
}