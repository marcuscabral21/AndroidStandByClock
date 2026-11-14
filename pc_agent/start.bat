@echo off
cd /d "%~dp0"
echo A abrir o tunel USB do agente...
adb reverse tcp:8765 tcp:8765
echo A iniciar o agente...
python agent.py
pause