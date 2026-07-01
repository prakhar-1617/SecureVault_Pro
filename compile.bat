@echo off
echo ===================================================
echo SecureVault Pro — Manual Compiler Script (No Maven)
echo ===================================================

:: Ensure output directory exists
if not exist bin mkdir bin

:: Find all Java source files and compile
echo [1/3] Locating source files...
dir /s /b src\main\java\*.java > sources.txt

echo [2/3] Compiling Java classes to bin/...
:: Support optional external JDBC driver JARs placed in lib/
if exist lib (
    javac -d bin -cp "lib/*" @sources.txt
) else (
    javac -d bin @sources.txt
)
del sources.txt

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

:: Copy resources (properties, sql, web files) to bin for classpath loading
echo [3/3] Syncing application resources to bin/...
xcopy /s /e /y src\main\resources\* bin\ > nul

echo ===================================================
echo [SUCCESS] Build completed. Run run.bat to start.
echo ===================================================
