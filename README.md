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

## Development verification

The release toolchain targets Java 8. Run every gate before producing an
installer or release jar:

```sh
lein do clean, test
lein cloverage
lein lint
lein uberjar
```

`lein cloverage` enforces an 80% floor for the deterministic application core
and writes HTML plus LCOV reports to `target/coverage`. Swing assembly,
entrypoints, the patched Seesaw compatibility namespaces, and Pronto's
macro-generated mapper forms are excluded from instrumentation; their behavior
remains covered by regression/round-trip tests and the live-display leak
canary. Run that
display-dependent check separately on a graphical host:

```sh
lein with-profile -dev run -m tvt.a7.profedit.leak-canary
```

`lein lint` checks the app and updater source/test trees with clj-kondo and
fails on warnings.
The test suite also validates generated profiles independently with Malli,
cross-checks them against the production Clojure Spec, and round-trips generated
payloads through both protobuf and JSON.

Both uberjar profiles keep Clojure reflection warnings enabled. CI performs a
clean AOT build and fails if either the app or updater emits a reflection
warning; do not suppress these warnings in release profiles.

GitHub Actions runs those gates for `main`, pull requests, tags, and manual
dispatches. Every run uploads the portable jar, its SHA-256 file, and coverage
reports. Trusted branch/tag/manual runs additionally build the Windows runtime
archive and installer; pushing a version-matching tag publishes those files as
a GitHub release. The Windows package includes firmware when the repository's
AWS firmware secrets are configured, and otherwise builds cleanly without the
optional bundled firmware. Repository URLs embedded into the app, updater,
launcher, and installer come from the current GitHub Actions repository, so the
same workflow is safe in upstream and fork repositories.
