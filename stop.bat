@echo off
REM Log Search Shutdown Script
REM Stops the running Log Search application

echo Stopping Log Search Application...

REM Find the process running log-search-1.0.0.jar
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FO LIST ^| findstr /C:"PID"') do (
    for /f "tokens=*" %%j in ('wmic process where "ProcessId=%%i" get CommandLine 2^>nul ^| findstr "log-search-1.0.0.jar"') do (
        echo Found Log Search process with PID: %%i
        taskkill /PID %%i /F >nul 2>&1
        if errorlevel 1 (
            echo Error: Failed to stop process %%i
            exit /b 1
        ) else (
            echo Log Search stopped successfully.
            exit /b 0
        )
    )
)

echo Log Search is not running.
exit /b 0
