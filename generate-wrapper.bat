@echo off
gradle :wrapper --gradle-version 8.9 --distribution-type bin
if errorlevel 1 exit /b %errorlevel%
