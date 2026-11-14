package com.example.pcmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

data class DailyForecast(
    val dateStr: String,
    val dayName: String,
    val maxTemp: Int,
    val minTemp: Int,
    val icon: String
)

@Composable
fun WeatherForecastPage() {
    var forecastList by remember { mutableStateOf<List<DailyForecast>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Busca a previsão diária de 7 dias na Open-Meteo para Vila Real
    LaunchedEffect(Unit) {
        try {
            val data = withContext(Dispatchers.IO) {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=41.3006&longitude=-7.7441&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val daily = json.getJSONObject("daily")
                val times = daily.getJSONArray("time")
                val maxs = daily.getJSONArray("temperature_2m_max")
                val mins = daily.getJSONArray("temperature_2m_min")
                val codes = daily.getJSONArray("weather_code")

                val list = mutableListOf<DailyForecast>()
                for (i in 0 until times.length()) {
                    val dateStr = times.getString(i)
                    val max = maxs.getDouble(i).toInt()
                    val min = mins.getDouble(i).toInt()
                    val code = codes.getInt(i)

                    val localDate = LocalDate.parse(dateStr)
                    val dayOfWeek = localDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))
                    val capitalizedDay = dayOfWeek.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }

                    val icon = when (code) {
                        0 -> "☀️"
                        in 1..3, in 45..48 -> "☁️"
                        in 51..67, in 80..99 -> "🌧️"
                        else -> "⛅"
                    }

                    list.add(DailyForecast(dateStr, capitalizedDay, max, min, icon))
                }
                list
            }
            forecastList = data
        } catch (e: Exception) {
            errorMessage = "Erro ao carregar previsão"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D12))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VILA REAL • PREVISÃO ESTENDIDA",
                        color = Color(0xFF7E8B9A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Próximos Dias",
                        color = Color(0xFFF0F4F8),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cartões dos dias da semana (estilo painel escuro)
            if (forecastList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    forecastList.take(7).forEachIndexed { index, forecast ->
                        val isToday = index == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isToday) Color(0xFF1E293B) else Color(0xFF141A26))
                                .border(1.dp, if (isToday) Color(0xFF3B82F6) else Color(0xFF232D42), RoundedCornerShape(16.dp))
                                .padding(vertical = 14.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = forecast.dayName.take(3).uppercase(),
                                    color = if (isToday) Color(0xFF3B82F6) else Color(0xFF7E8B9A),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = forecast.icon,
                                    fontSize = 24.sp
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${forecast.maxTemp}°",
                                        color = Color(0xFFF0F4F8),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${forecast.minTemp}°",
                                        color = Color(0xFF7E8B9A),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "A carregar previsão do tempo...",
                        color = Color(0xFF7E8B9A),
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}