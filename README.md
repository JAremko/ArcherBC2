## Dial Back to Windows XP while Editing Your Ballistic Profiles
*Enjoy the aesthetics of the long-gone Blissful era, when the hills were greener and the sky was a vibrant canvas of tranquility, punctuated by the serene simplicity of an uncluttered taskbar.*

* **[User Guide (English)](doc/user_guide/en/USER_GUIDE_EN.md)**
* **[Керівництво користувача (Українська)](doc/user_guide/ua/USER_GUIDE_UA.md)**

## Diagnostic logs

Running `java -jar profedit.jar` writes diagnostics to the console. The Windows
GUI executable has no console, so ArcherBC2 also keeps three rotating 1 MiB log
files in the platform's per-user log/state directory:

- Windows: `%LOCALAPPDATA%\ArcherBC2\logs\archerbc2-0.log`
- macOS: `~/Library/Logs/ArcherBC2/archerbc2-0.log`
- Linux: `${XDG_STATE_HOME:-~/.local/state}/archerbc2/logs/archerbc2-0.log`

If the preferred directory cannot be created, logging falls back to the system
temporary directory and never prevents the application from starting.

The persistent log includes startup/runtime details, wizard page transitions,
pending-input commits, validation problem paths, every error shown in the
bottom status bar, routine status-bar updates, save/load failures, background
device/update failures, and uncaught exceptions. Routine status updates are
kept at debug level in the file rather than printed to the console. Validation
summaries intentionally omit full profiles, profile names, and user notes.
