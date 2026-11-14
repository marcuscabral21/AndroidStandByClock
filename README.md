# AndroidStandByClock

Transforming an old mobile phone into a smart desk clock / standby bedside clock with PC hardware monitoring and Spotify remote control.

---

## App Overview

App built with Kotlin (Jetpack Compose) that displays on your old phone:
- **Digital Desk Clock & Calendar**: Giant clock with seconds, dynamic sizing, and night mode (red OLED theme for bedside use).
- **PC Hardware Monitor**: CPU, GPU, RAM, and VRAM (usage, temperature, and history graphs).
- **Spotify Remote Control**: Current track with album art, progress bar, and previous / play-pause / next buttons.

Sensors come from **LibreHardwareMonitor** running on your PC. Spotify is controlled directly from the phone via the Web API.

---

## 1. On PC

### LibreHardwareMonitor
1. Download LibreHardwareMonitor (github.com/LibreHardwareMonitor/LibreHardwareMonitor/releases).
2. Run it **as administrator** (required for CPU temperature reading).
3. Menu **Options → Remote Web Server → Run** (default port: 8085).
4. Test: open `http://localhost:8085/data.json` in your browser; JSON should appear.
5. Optional: Options → Start Minimized / Run On Windows Startup, and hide unused sensors.

### ADB (USB Tunnel)
1. Install Android Platform Tools.
2. On your phone: Settings → About phone → tap 7 times on "Build number" → Developer options → enable **USB Debugging**.
3. Connect via USB, accept authorization on phone, and run:

       adb reverse tcp:8085 tcp:8085

   Repeat this command whenever the cable is reconnected (or put it in a `.bat` script on Windows startup).

   **Alternative without ADB:** set `Config.kt` to your PC's IP, e.g. `http://192.168.1.50:8085/data.json` (phone and PC on the same Wi-Fi) and allow port 8085 in Windows Firewall.

---

## 2. Spotify (One-time Setup)

1. Go to https://developer.spotify.com/dashboard and create an app.
2. In **Redirect URIs** add exactly: `pcmonitor://callback`
3. In **APIs used** choose *Web API*.
4. Copy the **Client ID** and paste it into `app/src/main/java/com/example/pcmonitor/Config.kt` (`SPOTIFY_CLIENT_ID`).
5. In Spotify Dashboard → User Management, add your Spotify account email if in Development Mode.

**Premium** is required for playback buttons; track reading works on any account. Spotify must be open on your PC (or another device) to have an active device.

---

## 3. Build & Install

1. Install **Android Studio** (developer.android.com/studio).
2. `File → Open` and choose this folder. Wait for Gradle Sync.
3. Connect your phone via USB, select it at the top bar, and click **Run ▶**.
4. Open the app, tap **Connect to Spotify**, log in, and authorize.

---

## 4. Permanent Use

- The app keeps the screen awake and hides system bars.
- To prevent battery swelling on old phones connected permanently to a charger, enable charge limits if available on your device, and keep brightness low (AMOLED burn-in prevention).

---

## 5. Common Issues

| Symptom | Probable Cause |
|---|---|
| "PC offline" in red | LibreHardwareMonitor closed, web server off, or missing `adb reverse` |
| CPU temp in "--" | LibreHardwareMonitor not running as administrator |
| Spotify buttons error | No Premium or no active Spotify device |
| Spotify login doesn't return | Redirect URI not registered exactly as `pcmonitor://callback` |
| "INVALID_CLIENT" on login | `SPOTIFY_CLIENT_ID` empty or incorrect |

## 6. app/src/main/java/com/example/pcmonitor/Config.kt

Change the const val AGENT_TOKEN
