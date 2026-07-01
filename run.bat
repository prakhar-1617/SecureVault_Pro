@echo off
echo ===================================================
echo SecureVault Pro — Execution Launcher (No Maven)
echo ===================================================

if not exist bin (
    echo [ERROR] bin/ directory not found. Please compile the project first by running compile.bat
    pause
    exit /b 1
)

:: Run from the bin directory with classpath including optional JDBC JARs in lib/
if exist lib (
    java -cp "bin;lib/*" com.securevault.Main
) else (
    java -cp bin com.securevault.Main
)
