@echo off

REM The update URL is passed as the first argument to the script.
set update_url=%1

echo Updating...

bitsadmin /transfer myDownloadJob /download /priority normal %update_url% %cd%\profedit-new.jar

if exist profedit-new.jar (
    move /Y profedit-new.jar profedit.jar
    echo Update completed!
    profedit.exe
) else (
    echo Update failed! Please manually download the new version from the following URL:
    echo https://github.com/JAremko/profedit/tags
    pause
)
