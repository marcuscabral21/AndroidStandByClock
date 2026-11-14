"""
PC Monitor - agente do PC.

Pequeno servidor local (só aceita ligações de 127.0.0.1) que o telemóvel usa, através
do túnel `adb reverse`, para:
  - ler e alterar o volume, o mute do altifalante e o mute do microfone;
  - executar os atalhos definidos em shortcuts.json.

Segurança:
  - escuta apenas em 127.0.0.1 (nunca na rede);
  - exige o token de agent_config.json no cabeçalho X-Token;
  - rejeita pedidos vindos de páginas web (cabeçalho Origin) e Host diferente de localhost;
  - o telemóvel só pode pedir atalhos pelo ID; os comandos reais ficam no teu shortcuts.json.
"""
import ctypes
import hmac
import json
import os
import shutil
import subprocess
import threading
import time
import webbrowser
from ctypes import POINTER, cast
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from comtypes import CLSCTX_ALL, CoCreateInstance, CoInitialize
from pycaw.api.endpointvolume import IAudioEndpointVolume
from pycaw.api.mmdeviceapi import IMMDeviceEnumerator
from pycaw.constants import CLSID_MMDeviceEnumerator

BASE = Path(__file__).resolve().parent
CONFIG = json.loads((BASE / "agent_config.json").read_text(encoding="utf-8"))
TOKEN: str = CONFIG["token"]
PORT: int = int(CONFIG.get("port", 8765))
SHORTCUTS_FILE = BASE / "shortcuts.json"

# ---------------------------------------------------------------- áudio

E_RENDER, E_CAPTURE, E_MULTIMEDIA = 0, 1, 1


def _endpoint(flow: int):
    """Devolve o controlo de volume do dispositivo predefinido (saída ou entrada)."""
    enumerator = CoCreateInstance(CLSID_MMDeviceEnumerator, IMMDeviceEnumerator, CLSCTX_ALL)
    device = enumerator.GetDefaultAudioEndpoint(flow, E_MULTIMEDIA)
    interface = device.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
    return cast(interface, POINTER(IAudioEndpointVolume))


def audio_state() -> dict:
    out = {"volume": None, "muted": None, "mic_muted": None}
    try:
        speakers = _endpoint(E_RENDER)
        out["volume"] = round(speakers.GetMasterVolumeLevelScalar() * 100)
        out["muted"] = bool(speakers.GetMute())
    except Exception:
        pass
    try:
        mic = _endpoint(E_CAPTURE)
        out["mic_muted"] = bool(mic.GetMute())
    except Exception:
        pass
    return out


def set_volume(percent: int) -> None:
    percent = max(0, min(100, int(percent)))
    _endpoint(E_RENDER).SetMasterVolumeLevelScalar(percent / 100.0, None)


def toggle_mute() -> None:
    ep = _endpoint(E_RENDER)
    ep.SetMute(0 if ep.GetMute() else 1, None)


def toggle_mic() -> None:
    ep = _endpoint(E_CAPTURE)
    ep.SetMute(0 if ep.GetMute() else 1, None)


# ---------------------------------------------------------------- teclas

VK = {
    "ctrl": 0x11, "shift": 0x10, "alt": 0x12, "win": 0x5B,
    "enter": 0x0D, "esc": 0x1B, "tab": 0x09, "space": 0x20, "backspace": 0x08,
    "delete": 0x2E, "home": 0x24, "end": 0x23, "pageup": 0x21, "pagedown": 0x22,
    "left": 0x25, "up": 0x26, "right": 0x27, "down": 0x28, "printscreen": 0x2C,
    "playpause": 0xB3, "next": 0xB0, "prev": 0xB1, "stop": 0xB2,
    "volumeup": 0xAF, "volumedown": 0xAE, "volumemute": 0xAD,
}


def _vk(name: str) -> int:
    n = str(name).lower()
    if n in VK:
        return VK[n]
    if len(n) == 1 and n.isalnum():
        return ord(n.upper())
    if n.startswith("f") and n[1:].isdigit() and 1 <= int(n[1:]) <= 24:
        return 0x70 + int(n[1:]) - 1
    raise ValueError(f"Tecla desconhecida: {name}")


def send_hotkey(keys: list) -> None:
    codes = [_vk(k) for k in keys]
    user32 = ctypes.windll.user32
    for c in codes:
        user32.keybd_event(c, 0, 0, 0)
    for c in reversed(codes):
        user32.keybd_event(c, 0, 2, 0)  # 2 = KEYEVENTF_KEYUP


# ---------------------------------------------------------------- atalhos

def load_shortcuts() -> list:
    data = json.loads(SHORTCUTS_FILE.read_text(encoding="utf-8"))
    items = data.get("shortcuts", [])
    return [s for s in items if isinstance(s, dict) and s.get("id")]


def run_builtin(action: str) -> None:
    if action == "lock":
        ctypes.windll.user32.LockWorkStation()
    elif action == "sleep":
        # Nota: se a hibernação estiver ativa no Windows, isto pode hibernar em vez de suspender.
        subprocess.Popen(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"])
    elif action == "screenshot":
        send_hotkey(["win", "shift", "s"])
    elif action == "mute":
        toggle_mute()
    elif action == "mic":
        toggle_mic()
    else:
        raise ValueError(f"Ação interna desconhecida: {action}")


def run_shortcut(sc: dict) -> None:
    kind = sc.get("type")
    if kind == "builtin":
        run_builtin(sc["action"])
    elif kind == "hotkey":
        send_hotkey(sc["keys"])
    elif kind == "run":
        cmd = sc["command"]
        if not isinstance(cmd, list) or not cmd:
            raise ValueError("'command' tem de ser uma lista, ex.: [\"calc.exe\"]")
        subprocess.Popen([str(c) for c in cmd], cwd=sc.get("cwd"), shell=False)
    elif kind == "url":
        webbrowser.open(sc["url"])
    else:
        raise ValueError(f"Tipo de atalho desconhecido: {kind}")


# ---------------------------------------------------------------- servidor

class Handler(BaseHTTPRequestHandler):
    server_version = "PcAgent"

    def log_message(self, *args):  # sem ruído na consola
        pass

    def _send(self, code: int, obj: dict) -> None:
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        if self.headers.get("Origin"):
            return False  # pedidos de páginas web
        host = (self.headers.get("Host") or "").split(":")[0]
        if host not in ("127.0.0.1", "localhost"):
            return False
        return hmac.compare_digest(self.headers.get("X-Token", ""), TOKEN)

    def _json_body(self) -> dict:
        length = min(int(self.headers.get("Content-Length") or 0), 4096)
        if length <= 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def do_GET(self):
        self._route("GET")

    def do_POST(self):
        self._route("POST")

    def _route(self, method: str) -> None:
        if not self._authorized():
            return self._send(401, {"error": "não autorizado"})
        path = self.path.split("?")[0]
        try:
            if method == "GET" and path == "/state":
                return self._send(200, audio_state())

            if method == "GET" and path == "/shortcuts":
                public = [
                    {"id": s["id"], "label": s.get("label", s["id"]), "icon": s.get("icon", "⚙")}
                    for s in load_shortcuts()
                ]
                return self._send(200, {"shortcuts": public})

            if method == "POST" and path == "/volume":
                set_volume(self._json_body()["value"])
                return self._send(200, audio_state())

            if method == "POST" and path == "/mute":
                toggle_mute()
                return self._send(200, audio_state())

            if method == "POST" and path == "/mic":
                toggle_mic()
                return self._send(200, audio_state())

            if method == "POST" and path.startswith("/run/"):
                sid = path[len("/run/"):]
                match = next((s for s in load_shortcuts() if s["id"] == sid), None)
                if match is None:
                    return self._send(404, {"error": f"atalho '{sid}' não existe"})
                run_shortcut(match)
                return self._send(200, {"ok": True})

            return self._send(404, {"error": "rota desconhecida"})
        except Exception as e:  # erro devolvido ao telemóvel para aparecer no ecrã
            return self._send(500, {"error": f"{type(e).__name__}: {e}"})


LOG_FILE = BASE / "agent.log"


def log(msg: str) -> None:
    """Escreve em agent.log (útil quando corre sem janela, com pythonw)."""
    try:
        with LOG_FILE.open("a", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + msg + "\n")
    except Exception:
        pass


def _find_adb():
    found = shutil.which("adb")
    if found:
        return found
    local = os.environ.get("LOCALAPPDATA")
    if local:
        candidate = Path(local) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if candidate.exists():
            return str(candidate)
    return None


def keep_tunnel_alive() -> None:
    """Refaz o túnel USB a cada 10 s: ele desaparece quando o cabo é ligado/desligado."""
    adb = _find_adb()
    if adb is None:
        log("adb não encontrado: o túnel USB não é criado automaticamente.")
        return
    log(f"A usar o adb em {adb}")
    no_window = 0x08000000 if os.name == "nt" else 0  # CREATE_NO_WINDOW
    while True:
        try:
            subprocess.run(
                [adb, "reverse", f"tcp:{PORT}", f"tcp:{PORT}"],
                capture_output=True, timeout=15, creationflags=no_window,
            )
        except Exception:
            pass
        time.sleep(10)


def main() -> None:
    try:
        CoInitialize()
        server = HTTPServer(("127.0.0.1", PORT), Handler)
    except Exception as e:  # por exemplo: já há outro agente a correr na mesma porta
        log(f"Não foi possível arrancar: {type(e).__name__}: {e}")
        raise
    threading.Thread(target=keep_tunnel_alive, daemon=True).start()
    log(f"Agente a correr em 127.0.0.1:{PORT}")
    print(f"Agente a correr em 127.0.0.1:{PORT}. Ctrl+C para sair.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
