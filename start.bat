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

echo Starting Log Search Application...
echo.

java -jar "%JAR_FILE%" %CONFIG_ARG% %*
