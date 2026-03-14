@echo off
REM Log Search Startup Script for Windows
REM This script makes it easy to run the application with external configuration

SET JAR_FILE=target\log-search-1.0.0.jar
SET CONFIG_FILE=config\application.yml

REM Check if JAR exists
IF NOT EXIST "%JAR_FILE%" (
    echo Error: JAR file not found: %JAR_FILE%
    echo Please run: mvn clean package
    exit /b 1
)

REM Check if external config exists
IF EXIST "%CONFIG_FILE%" (
    echo Using external configuration: %CONFIG_FILE%
    SET CONFIG_ARG=--spring.config.location=file:%CONFIG_FILE%
) ELSE (
    echo No external config found, using defaults from JAR
    SET CONFIG_ARG=
)

REM Default JVM heap settings
SET HEAP_MIN=2g
SET HEAP_MAX=4g
SET JVM_EXTRA_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200

REM Parse JVM heap settings from config file (if exists)
IF EXIST "%CONFIG_FILE%" (
    FOR /F "tokens=2 delims=:" %%A IN ('findstr /R "heap-min:" "%CONFIG_FILE%"') DO (
        SET HEAP_MIN_LINE=%%A
    )
    FOR /F "tokens=2 delims=:" %%A IN ('findstr /R "heap-max:" "%CONFIG_FILE%"') DO (
        SET HEAP_MAX_LINE=%%A
    )
    FOR /F "tokens=2* delims=:" %%A IN ('findstr /R "extra-opts:" "%CONFIG_FILE%"') DO (
        SET JVM_EXTRA_LINE=%%A
    )

    REM Extract values from lines (remove spaces and default syntax)
    IF DEFINED HEAP_MIN_LINE (
        FOR /F "tokens=2 delims=:" %%B IN ("!HEAP_MIN_LINE!") DO SET HEAP_MIN_TEMP=%%B
        IF DEFINED HEAP_MIN_TEMP (
            FOR /F "tokens=1 delims=}" %%C IN ("!HEAP_MIN_TEMP!") DO SET HEAP_MIN=%%C
            SET HEAP_MIN=!HEAP_MIN: =!
        )
    )

    IF DEFINED HEAP_MAX_LINE (
        FOR /F "tokens=2 delims=:" %%B IN ("!HEAP_MAX_LINE!") DO SET HEAP_MAX_TEMP=%%B
        IF DEFINED HEAP_MAX_TEMP (
            FOR /F "tokens=1 delims=}" %%C IN ("!HEAP_MAX_TEMP!") DO SET HEAP_MAX=%%C
            SET HEAP_MAX=!HEAP_MAX: =!
        )
    )

    IF DEFINED JVM_EXTRA_LINE (
        SET JVM_EXTRA_OPTS=!JVM_EXTRA_LINE:~1!
        SET JVM_EXTRA_OPTS=!JVM_EXTRA_OPTS: ${JVM_EXTRA_OPTS:=!
        SET JVM_EXTRA_OPTS=!JVM_EXTRA_OPTS:}=!
    )
)

REM Allow environment variables to override config file
IF DEFINED JVM_HEAP_MIN SET HEAP_MIN=%JVM_HEAP_MIN%
IF DEFINED JVM_HEAP_MAX SET HEAP_MAX=%JVM_HEAP_MAX%

REM Build JVM arguments
SET JVM_ARGS=-Xms%HEAP_MIN% -Xmx%HEAP_MAX% %JVM_EXTRA_OPTS%

echo Starting Log Search Application...
echo JVM Settings: Min Heap=%HEAP_MIN%, Max Heap=%HEAP_MAX%
echo Extra JVM Options: %JVM_EXTRA_OPTS%
echo.

java %JVM_ARGS% -jar "%JAR_FILE%" %CONFIG_ARG% %*
