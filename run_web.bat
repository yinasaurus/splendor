@echo off
setlocal enabledelayedexpansion

set CP=classes
if exist lib\*.jar (
    for %%j in (lib\*.jar) do set CP=!CP!;%%j
)

java -cp "!CP!" splendor.web.WebServer
