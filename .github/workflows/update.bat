@echo off

echo Updating...

REM Fetch the latest version from the github api and extract the version from the response
for /f "delims=" %%i in ('curl -s https://api.github.com/repos/JAremko/profedit/releases/latest ^| findstr "tag_name"') do set version=%%i
set version=%version:~16,-2%

REM Now construct the download url
set update_url=https://github.com/JAremko/profedit/releases/download/%version%/profedit.jar

REM Try to delete the file until successful
:loop
del profedit.jar
if exist profedit.jar (
    echo Waiting for file to be released...
    timeout /t 1 /nobreak
    goto loop
)

bitsadmin /transfer myDownloadJob /download /priority normal %update_url% %cd%\profedit.jar

if exist profedit.jar (
    echo Update completed!
) else (
    echo Update failed! Please manually download the new version from the following URL:
    echo https://github.com/JAremko/profedit/tags
    pause
)

REM Relaunch the application
start profedit.exe
