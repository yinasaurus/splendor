@echo off
setlocal enabledelayedexpansion

REM Create classes directory if it doesn't exist
if not exist "classes" mkdir classes

echo Cleaning old class files...
del /s /q classes\*.class 2>nul

REM Compile all Java source files using sourcepath
javac -d classes -cp "lib/*;classes" -sourcepath src ^
  src\splendor\main\SplendorGame.java ^
  src\splendor\web\WebServer.java

if %ERRORLEVEL% EQU 0 (
    echo Compilation complete. Class files are in the classes directory.
) else (
    echo Compilation failed. Please check for errors.
    exit /b 1
)
