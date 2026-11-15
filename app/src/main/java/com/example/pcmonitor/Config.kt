package com.example.pcmonitor

/**
 * Configuração da app. Edita estes valores antes de compilar.
 */
object Config {
    /** Client ID da tua app em https://developer.spotify.com/dashboard */
    const val SPOTIFY_CLIENT_ID = "YOUR_AGENT_TOKEN_HERE"

    /** Tem de estar registado EXATAMENTE assim nas definições da app Spotify. */
    const val SPOTIFY_REDIRECT_URI = "pcmonitor://callback"

    /**
     * Endereço do servidor web do LibreHardwareMonitor.
     * Com `adb reverse tcp:8085 tcp:8085` o telemóvel acede ao PC por 127.0.0.1.
     * Em alternativa (Wi-Fi), usa o IP do PC, por exemplo "http://192.168.1.50:8085/data.json".
     */
    const val LHM_URL = "http://127.0.0.1:8085/data.json"

    /** Agente do PC (pc_agent/agent.py): volume, microfone e atalhos. Chega pelo `adb reverse tcp:8765`. */
    const val AGENT_URL = "http://127.0.0.1:8765"

    /** Tem de ser igual ao "token" em pc_agent/agent_config.json. */
    const val AGENT_TOKEN = "YOUR_AGENT_TOKEN_HERE"

    const val SENSOR_INTERVAL_MS = 1500L
    const val SPOTIFY_INTERVAL_MS = 2000L
    const val HISTORY_POINTS = 60
}
