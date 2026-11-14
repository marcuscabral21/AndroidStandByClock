# PC Monitor — dashboard no telemóvel Android

App Kotlin (Jetpack Compose) que mostra no telemóvel:

- CPU, GPU, RAM e VRAM (uso, temperatura e gráfico dos últimos ~90 s)
- Faixa do Spotify com capa, barra de progresso e botões anterior / play-pause / seguinte

Os sensores vêm do **LibreHardwareMonitor** (corre no PC). O Spotify é controlado
**diretamente pelo telemóvel** através da Web API, sem passar pelo PC.

---

## 1. No PC

### LibreHardwareMonitor
1. Descarrega o LibreHardwareMonitor (github.com/LibreHardwareMonitor/LibreHardwareMonitor/releases).
2. Executa-o **como administrador** (necessário para ler a temperatura da CPU).
3. Menu **Options → Remote Web Server → Run** (porta predefinida: 8085).
4. Teste: abre `http://localhost:8085/data.json` no browser; deve aparecer JSON.
5. Opcional: Options → Start Minimized / Run On Windows Startup, e esconde os sensores
   que não precisas (discos, rato...) para gastar ainda menos CPU.

### ADB (túnel USB)
1. Instala as *Platform Tools* do Android (ou usa as do Android Studio).
2. No telemóvel: Definições → Sobre o telefone → toca 7x em "Número de compilação" →
   Opções de programador → ativa **Depuração USB**.
3. Liga o cabo, aceita o pedido de autorização no telemóvel e corre:

       adb reverse tcp:8085 tcp:8085

   Tens de repetir este comando sempre que o cabo for ligado de novo.
   Podes guardá-lo num ficheiro `.bat` e pô-lo no arranque do Windows.

   **Alternativa sem ADB:** põe em `Config.kt` o IP do PC, por exemplo
   `http://192.168.1.50:8085/data.json` (telemóvel e PC na mesma rede) e permite a
   porta 8085 na firewall do Windows.

---

## 2. Spotify (uma vez)

1. Vai a https://developer.spotify.com/dashboard e cria uma app.
2. Em **Redirect URIs** adiciona exatamente: `pcmonitor://callback`
3. Em **APIs used** escolhe *Web API*.
4. Copia o **Client ID** e cola-o em
   `app/src/main/java/com/example/pcmonitor/Config.kt` (`SPOTIFY_CLIENT_ID`).
5. Na app Spotify Dashboard, em *User Management*, adiciona o email da tua conta
   Spotify se a app estiver em Development Mode.

Precisas de **Premium** para os botões funcionarem; a leitura da faixa funciona
em qualquer conta. O Spotify tem de estar aberto no PC (ou noutro dispositivo)
para existir um "dispositivo ativo".

---

## 3. Compilar e instalar

1. Instala o **Android Studio** (developer.android.com/studio).
2. `File → Open` e escolhe esta pasta (`PcMonitor`). Aguarda o *Gradle Sync*
   (precisa de internet na primeira vez).
3. Liga o telemóvel por USB (com a depuração ativa), escolhe-o na barra superior e
   carrega em **Run ▶**.
4. Abre a app, toca em **Ligar ao Spotify**, faz login e aceita.
   O browser devolve-te à app automaticamente.

Se o Android Studio reclamar do Gradle Wrapper, cria um projeto novo
"Empty Activity" (Compose, package `com.example.pcmonitor`, minSdk 26) e copia para
lá a pasta `app/src/main` e os `build.gradle.kts` deste projeto.

---

## 3b. Agente do PC: volume, microfone e atalhos

A app tem **duas páginas** (desliza para trocar): monitorização e controlos.
A página de controlos precisa do pequeno programa em `pc_agent/`.

1. Instala o Python 3 no Windows (python.org, com "Add to PATH").
2. Na pasta `pc_agent`: `pip install -r requirements.txt`
3. Corre `start.bat` (abre os dois túneis USB e inicia o agente).
   Deixa a janela aberta; podes pô-lo no arranque do Windows.

O token de segurança já vem preenchido nos dois lados (`agent_config.json` e
`Config.kt`). Se o mudares, tem de ser igual nos dois.

### Escolher os teus atalhos
Edita `pc_agent/shortcuts.json` quando quiseres. **Não é preciso recompilar nem
reiniciar nada**: a app relê a lista a cada ~10 segundos. Cada atalho é um bloco:

    { "id": "unico", "label": "Nome no botão", "icon": "🎮", "type": "...", ... }

Tipos disponíveis:

| type | campos | exemplo |
|---|---|---|
| `run` | `command`: lista (programa + argumentos) | `"command": ["C:\\Program Files\\Steam\\steam.exe"]` |
| `hotkey` | `keys`: lista de teclas | `"keys": ["ctrl", "shift", "m"]` |
| `url` | `url` | `"url": "https://www.youtube.com"` |
| `builtin` | `action`: `lock`, `sleep`, `screenshot`, `mute`, `mic` | `"action": "lock"` |

Teclas aceites em `hotkey`: letras, números, `f1`..`f24`, `ctrl`, `shift`, `alt`, `win`,
`enter`, `esc`, `tab`, `space`, `backspace`, `delete`, setas, `home`, `end`, `pageup`,
`pagedown`, `printscreen`, `playpause`, `next`, `prev`, `stop`, `volumeup`,
`volumedown`, `volumemute`. Nos caminhos do Windows, escreve `\\` em vez de `\`.
Se o JSON tiver um erro de sintaxe, os atalhos desaparecem até o corrigires.

### Segurança
- O agente só escuta em `127.0.0.1` (nunca na rede) e exige o token.
- O telemóvel só pede atalhos pelo `id`; os comandos reais vivem no teu `shortcuts.json`.
- Quem puder editar `shortcuts.json` ou ler `agent_config.json` no PC controla o agente, por
  isso não os partilhes.

---

## 4. Uso permanente

- A app mantém o ecrã sempre ligado e esconde as barras do sistema.
- Para o ecrã não apagar nunca com o cabo ligado: Opções de programador →
  **Manter ativo** (Stay awake).
- Bateria: um telemóvel antigo sempre ligado ao carregador pode fazer a bateria
  inchar. Se o telemóvel tiver limite de carga (80%), ativa-o, e vai verificando
  que a traseira não levanta.
- Brilho baixo poupa o ecrã (AMOLED tem risco de *burn-in* com layouts estáticos).

---

## 5. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| "PC sem resposta" em vermelho | LibreHardwareMonitor fechado, servidor web desligado, ou falta o `adb reverse` |
| Temperatura da CPU em "--" | LibreHardwareMonitor não está a correr como administrador |
| GPU/VRAM em "--" | Sensor com nome diferente na tua placa; vê `data.json` e ajusta `LhmParser.kt` |
| Botões do Spotify dão erro | Sem Premium, ou nenhum dispositivo Spotify ativo |
| Login do Spotify não volta à app | Redirect URI não registado exatamente como `pcmonitor://callback` |
| "INVALID_CLIENT" no login | `SPOTIFY_CLIENT_ID` por preencher ou errado |

---

## Estrutura

    app/src/main/java/com/example/pcmonitor/
      Config.kt            -> client ID, URL do PC, intervalos
      LhmParser.kt         -> lê e interpreta o JSON do LibreHardwareMonitor
      SpotifyClient.kt     -> login PKCE, tokens, leitura e comandos
      MonitorViewModel.kt  -> ciclos de atualização e estado
      DashboardScreen.kt   -> interface (Compose)
      MainActivity.kt      -> ecrã inteiro, ecrã sempre ligado, retorno do login
      AgentClient.kt       -> fala com o agente do PC (volume, mic, atalhos)
      ControlsPage.kt      -> página de controlos
    pc_agent/
      agent.py             -> servidor local no PC (volume, microfone, atalhos)
      shortcuts.json       -> os teus atalhos (editável a qualquer momento)
      agent_config.json    -> token e porta
      start.bat            -> abre os túneis USB e inicia o agente
