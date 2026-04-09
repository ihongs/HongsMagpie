@echo off

SETLOCAL

set CURR_PATH=%~DP0

cd /d "%CURR_PATH%"

uv run parser.py --file "%1"

ENDLOCAL

@echo on