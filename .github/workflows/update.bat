@echo off

echo Updating...

REM The URL where the latest version can be downloaded
set download_url=https://github.com/JAremko/profedit/releases/latest/download/profedit.jar

REM Download the new version
bitsadmin /transfer myDownloadJob /download /priority normal %download_url% %cd%\profedit-new.jar

if exist profedit-new.jar (
    echo Update downloaded successfully, waiting for the application to close...

    :wait
    ping -n 2 127.0.0.1 >nul
    2>nul ren profedit.jar profedit.jar.bak && (
        ren profedit.jar.bak profedit.jar
    ) || (
        goto wait
    )

    echo Application closed, proceeding with the update.

    if exist profedit.jar (
        del /F /Q profedit.jar
    )

    move /Y profedit-new.jar profedit.jar
    echo Update completed!
    start "" profedit.exe
) else (
    echo Update failed! Please manually download the new version from the following URL:
    echo https://github.com/JAremko/profedit/releases
    pause
)
