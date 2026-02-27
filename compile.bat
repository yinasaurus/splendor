@echo off
setlocal enabledelayedexpansion

REM Create classes directory if it doesn't exist
if not exist "classes" mkdir classes

REM Compile all Java source files using sourcepath
javac -d classes -cp "lib/*;classes" -sourcepath src src\splendor\main\SplendorGame.java

if %ERRORLEVEL% EQU 0 (
    echo Compilation complete. Class files are in the classes directory.
) else (
    echo Compilation failed. Please check for errors.
    exit /b 1
)
