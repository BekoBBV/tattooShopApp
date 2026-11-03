@echo off
rem Starts Tattoo Studio with fresh demo data: deletes ALL bookings, messages, reviews, logins,
rem services and artists, then fills the database with the original demo data again.
rem Use it before a presentation; afterwards start normally with start.bat.

netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul
if not errorlevel 1 (
    echo Tattoo Studio is running. Close its window first, then run this again.
    pause
    exit /b 1
)

echo This deletes all bookings, messages, reviews and logins and restores the demo data.
choice /c YN /m "Continue"
if errorlevel 2 exit /b 0
call "%~dp0start.bat" --reset-demo
