package com.example.pcmonitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

class NotAuthorizedException : Exception("Sessão do Spotify expirada")

data class Playback(
    val title: String,
    val artist: String,
    val album: String,
    val artUrl: String?,
    val isPlaying: Boolean,
    val progressMs: Long,
    val durationMs: Long,
    val device: String,
)

/**
 * Cliente mínimo da Spotify Web API com login PKCE (não precisa de client secret).
 * Os comandos exigem Spotify Premium.
 */
class SpotifyClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getString("refresh_token", null) != null

    fun logout() = prefs.edit().clear().apply()

    // ---------- Login ----------

    /** Abre o browser na página de login do Spotify. */
    fun startLogin(context: Context) {
        val verifier = randomString(64)
        prefs.edit().putString("verifier", verifier).apply()

        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", Config.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", Config.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge(verifier))
            .build()

        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Chamado quando o Spotify devolve o utilizador à app. Corre em thread de rede. */
    fun finishLogin(redirect: Uri) {
        val code = redirect.getQueryParameter("code")
            ?: error("Login cancelado: ${redirect.getQueryParameter("error")}")
        val verifier = prefs.getString("verifier", null) ?: error("Falta o code_verifier")

        val body = form(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to Config.SPOTIFY_REDIRECT_URI,
            "client_id" to Config.SPOTIFY_CLIENT_ID,
            "code_verifier" to verifier,
        )
        val (status, text) = http("POST", TOKEN_URL, null, body)
        if (status != 200) error("Erro no token ($status): $text")
        saveTokens(JSONObject(text))
    }

    // ---------- Leitura e comandos ----------

    fun currentPlayback(): Playback? {
        // Adicionado '?additional_types=track,episode' para o Spotify devolver também os podcasts
        val (status, text) = authed("GET", "$API/me/player?additional_types=track,episode")
        if (status == 204 || text.isBlank()) return null
        if (status != 200) error("Spotify respondeu $status")

        val json = JSONObject(text)
        val item = json.optJSONObject("item") ?: return null
        val isEpisode = json.optString("currently_playing_type") == "episode" || item.has("show")

        val artistNames: String
        val albumName: String
        val art: String?

        if (isEpisode) {
            // Tratamento para Podcasts (Episódios)
            val show = item.optJSONObject("show")
            artistNames = show?.optString("publisher").orEmpty()
            albumName = show?.optString("name").orEmpty()
            val images = item.optJSONArray("images") ?: show?.optJSONArray("images")
            art = images?.let { it.optJSONObject(minOf(1, it.length() - 1))?.optString("url") }
        } else {
            // Tratamento normal para Músicas
            val artists = item.optJSONArray("artists")
            artistNames = buildList {
                if (artists != null) for (i in 0 until artists.length()) add(artists.getJSONObject(i).optString("name"))
            }.joinToString(", ")
            val album = item.optJSONObject("album")
            albumName = album?.optString("name").orEmpty()
            val images = album?.optJSONArray("images")
            art = images?.let { it.optJSONObject(minOf(1, it.length() - 1))?.optString("url") }
        }

        return Playback(
            title = item.optString("name"),
            artist = artistNames,
            album = albumName,
            artUrl = art,
            isPlaying = json.optBoolean("is_playing"),
            progressMs = json.optLong("progress_ms"),
            durationMs = item.optLong("duration_ms"),
            device = json.optJSONObject("device")?.optString("name").orEmpty(),
        )
    }

    fun play() = command("PUT", "/me/player/play")
    fun pause() = command("PUT", "/me/player/pause")
    fun next() = command("POST", "/me/player/next")
    fun previous() = command("POST", "/me/player/previous")

    private fun command(method: String, path: String) {
        val (status, text) = authed(method, "$API$path")
        // 204 = ok; 404 = nenhum dispositivo ativo; 403 = sem Premium
        if (status == 404) error("Nenhum dispositivo Spotify ativo")
        if (status == 403) error("Comando recusado (precisa de Premium?)")
        if (status >= 400) error("Spotify respondeu $status: $text")
    }

    // ---------- Tokens ----------

    private fun accessToken(): String {
        val expiresAt = prefs.getLong("expires_at", 0)
        val token = prefs.getString("access_token", null)
        if (token != null && System.currentTimeMillis() < expiresAt - 30_000) return token
        return refresh()
    }

    private fun refresh(): String {
        val refreshToken = prefs.getString("refresh_token", null) ?: throw NotAuthorizedException()
        val body = form(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to Config.SPOTIFY_CLIENT_ID,
        )
        val (status, text) = http("POST", TOKEN_URL, null, body)
        if (status == 400 || status == 401) {
            logout()
            throw NotAuthorizedException()
        }
        if (status != 200) error("Erro ao renovar token ($status)")
        saveTokens(JSONObject(text))
        return prefs.getString("access_token", null)!!
    }

    private fun saveTokens(json: JSONObject) {
        val editor = prefs.edit()
            .putString("access_token", json.getString("access_token"))
            .putLong("expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
        // O refresh_token só vem se o Spotify o rodar; senão mantém-se o anterior.
        json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { editor.putString("refresh_token", it) }
        editor.apply()
    }

    private fun authed(method: String, url: String): Pair<Int, String> {
        var result = http(method, url, accessToken(), null)
        if (result.first == 401) result = http(method, url, refresh(), null)
        if (result.first == 401) throw NotAuthorizedException()
        return result
    }

    // ---------- HTTP e utilitários ----------

    private fun http(method: String, url: String, bearer: String?, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            bearer?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (method != "GET") {
                // Os PUT/POST sem corpo precisam de Content-Length: 0
                conn.doOutput = true
                if (body != null) conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { out -> body?.let { out.write(it.toByteArray()) } }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun form(vararg pairs: Pair<String, String>) =
        pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rnd = SecureRandom()
        return buildString { repeat(length) { append(chars[rnd.nextInt(chars.length)]) } }
    }

    private fun challenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val API = "https://api.spotify.com/v1"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing"
    }
}