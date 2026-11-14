package com.example.pcmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

@Composable
fun StandByClockPage(onWeatherClick: () -> Unit = {}) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var temperature by remember { mutableStateOf<String?>(null) }
    var weatherIcons by remember { mutableStateOf("") }

    // Estado do Modo Noturno (Vermelho)
    var isNightMode by remember { mutableStateOf(false) }

    val cardBackground = if (isNightMode) Color(0xFF1F0505) else Color(0xFF141A26)
    val cardBorder = if (isNightMode) Color(0xFF4A1010) else Color(0xFF232D42)
    val textColor = if (isNightMode) Color(0xFFFF4D4D) else Color(0xFFF0F4F8)
    val dimColor = if (isNightMode) Color(0xFF992626) else Color(0xFF7E8B9A)
    val activeColor = if (isNightMode) Color(0xFFFF3333) else Color(0xFF3B82F6)

    // Atualiza o relógio a cada segundo
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Atualiza o estado do tempo de Vila Real a cada 5 minutos usando o wttr.in
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val data = withContext(Dispatchers.IO) {
                    val url = URL("https://wttr.in/Vila+Real,Portugal?format=j1")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)

                    val current = json.getJSONArray("current_condition").getJSONObject(0)
                    val tempC = current.getString("temp_C")
                    val windKmph = current.getString("windspeedKmph").toDoubleOrNull() ?: 0.0
                    val precipMM = current.getString("precipMM").toDoubleOrNull() ?: 0.0
                    val weatherCode = current.getString("weatherCode").toIntOrNull() ?: 113
                    val desc = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value").lowercase()

                    val isClearSky = weatherCode == 113
                    val isCloudy = weatherCode in listOf(116, 119, 122, 143, 248)

                    val isRaining = precipMM > 0.0 ||
                            weatherCode >= 176 ||
                            desc.contains("rain") ||
                            desc.contains("drizzle") ||
                            desc.contains("shower")

                    val isWindy = windKmph > 25.0

                    val icons = buildString {
                        if (isClearSky) append(" ☀️")
                        else if (isCloudy) append(" ☁️")

                        if (isRaining) append(" 🌧️")
                        if (isWindy) append(" 💨")
                    }

                    Pair("${tempC}°C", icons)
                }
                temperature = data.first
                weatherIcons = data.second
            } catch (e: Exception) {
                // Mantém os valores anteriores se falhar a rede
            }
            delay(300_000)
        }
    }

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isNightMode) Color.Black else Color(0xFF090D12))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lado Esquerdo: Cartão do Relógio Digital Gigante
        Box(
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBackground)
                .border(1.5.dp, cardBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DigitalClock(
                calendar = calendar,
                textColor = textColor,
                dimColor = dimColor,
                cardBorder = cardBorder
            )
        }

        // Lado Direito: Cartão do Painel de Informação (Tempo + Calendário)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBackground)
                .border(1.5.dp, cardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Cabeçalho: Temperatura de Vila Real + Botão Noturno (Clicável para ir à 3ª página)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBorder.copy(alpha = 0.4f))
                        .clickable { onWeatherClick() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VILA REAL",
                            color = dimColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (temperature != null) "$temperature$weatherIcons" else "A carregar...",
                            color = textColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(cardBackground)
                            .border(1.dp, cardBorder, CircleShape)
                            .clickable { isNightMode = !isNightMode },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isNightMode) "🌙" else "☀️",
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendário estruturado
                CalendarWidget(
                    calendar = calendar,
                    textColor = textColor,
                    dimColor = dimColor,
                    activeColor = activeColor
                )
            }
        }
    }
}

@Composable
fun DigitalClock(
    calendar: Calendar,
    textColor: Color,
    dimColor: Color,
    cardBorder: Color
) {
    val hour = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.HOUR_OF_DAY))
    val minute = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.MINUTE))
    val second = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.SECOND))

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val dynamicFontSize = (maxWidth.value * 0.31f).sp

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$hour:$minute",
                color = textColor,
                fontSize = dynamicFontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = (-6).sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardBorder.copy(alpha = 0.45f))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(textColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = second,
                    color = textColor,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SEG",
                    color = dimColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
fun CalendarWidget(
    calendar: Calendar,
    textColor: Color,
    dimColor: Color,
    activeColor: Color
) {
    val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())?.uppercase() ?: ""
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

    val tempCal = calendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    val maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val startDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = monthName,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { day ->
                Text(
                    text = day,
                    color = dimColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val days = mutableListOf<Int?>()
        repeat(startDayOfWeek) { days.add(null) }
        for (d in 1..maxDays) { days.add(d) }
        while (days.size % 7 != 0) { days.add(null) }

        val weeks = days.chunked(7)

        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .then(
                                if (day == currentDay) {
                                    Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(activeColor)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Text(
                                text = day.toString(),
                                color = if (day == currentDay) Color.White else textColor,
                                fontSize = 14.sp,
                                fontWeight = if (day == currentDay) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}