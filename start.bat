@echo off
setlocal
rem Tattoo Studio: builds the site and starts the server as one app on http://localhost:8080.
rem Double-click to start; close this window (or press Ctrl+C) to stop.
rem Extra arguments go to the server, e.g. "start.bat --reset-demo" (see reset-demo.bat).

title Tattoo Studio
cd /d "%~dp0"
set "URL=http://localhost:8080"

rem Already running? Then just open it.
netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul
if not errorlevel 1 (
    echo Tattoo Studio is already running.
    if not defined FS_NO_BROWSER start "" "%URL%"
    timeout /t 3 >nul
    exit /b 0
)

rem Java: the JDK IntelliJ uses, unless JAVA_HOME is already set
if not defined JAVA_HOME if exist "%USERPROFILE%\.jdks\openjdk-27\bin\java.exe" set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-27"
if not defined JAVA_HOME (
    where java >nul 2>nul || (echo Java not found. Install a JDK 17+ or set JAVA_HOME. & pause & exit /b 1)
)

where npm >nul 2>nul || (echo Node.js not found. Install it from https://nodejs.org & pause & exit /b 1)

if not exist "server\src\main\resources\secrets.properties" (
    echo server\src\main\resources\secrets.properties is missing.
    echo Copy secrets.properties.example next to it and fill it in ^(see README^).
    pause & exit /b 1
)

echo [1/2] Building the website...
pushd client
if not exist node_modules (
    call npm ci || (echo npm install failed. & popd & pause & exit /b 1)
)
call npm run build || (echo Website build failed. & popd & pause & exit /b 1)
popd

echo [2/2] Starting the server (first start takes a little longer)...
rem Opens the browser as soon as the server answers
if not defined FS_NO_BROWSER start "" /b powershell -NoProfile -WindowStyle Hidden -Command ^
  "for ($i = 0; $i -lt 90; $i++) { try { if ((Invoke-WebRequest '%URL%/api/health' -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { Start-Process '%URL%'; break } } catch {} Start-Sleep 2 }"

pushd server
call "%~dp0server\mvnw.cmd" -q spring-boot:run "-Dspring-boot.run.arguments=--app.shop.website-url=%URL% %*"
popd

echo.
echo The server has stopped.
pause
