@echo off
setlocal enabledelayedexpansion

if not exist "classes" mkdir classes

echo Cleaning old class files...
del /s /q classes\*.class 2>nul

set CP=classes
if exist lib\*.jar (
    for %%j in (lib\*.jar) do set CP=!CP!;%%j
)

set FILES=
for /r src %%f in (*.java) do set FILES=!FILES! "%%f"

javac -d classes -cp "!CP!" -sourcepath src !FILES!

if errorlevel 1 (
    echo Compilation failed. Please check for errors.
    exit /b 1
)
echo Compilation complete. Class files are in the classes directory.
